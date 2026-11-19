// Trabajador externo JavaScript (Node 18+, sin dependencias) para las External Tasks de los tres procesos.
//  - trasladar-venta (PR-CO-03): evento de mensaje de envío; inicia PR-SO-05 con el mensaje VentaEntregada.
//  - constancia-acta-equipo (PR-SO-04): constancia con huella SHA-256 del acta de entrega.
//  - enviar-factura (PR-SO-05): evento de mensaje de envío; arma la factura y su huella SHA-256.
// No simula correo ni firma digital: deja el contenido y la huella como variables de la instancia.
const { createHash } = require('node:crypto');
const base = process.env.CAMUNDA_URL || 'http://127.0.0.1:8080/engine-rest';
const user = process.env.CAMUNDA_USER || 'admin';
const password = process.env.CAMUNDA_ADMIN_PASSWORD || 'admin';
const workerId = 'agua-cumbre-node';
const VENTA = ['pedidoId','clienteId','canal','valorVenta','loteId','cantidad','zona'];
const topics = {
 'trasladar-venta': [...VENTA,'solicitantePortal'],
 'constancia-acta-equipo': ['equipoId','responsableId','actaEntrega'],
 'enviar-factura': ['numeroFactura','documentoId','clienteId','valorVenta','saldo'],
};
const texto = (v,k) => { const x=v[k]?.value; if(typeof x!=='string'||!x.trim()) throw new Error('Variable requerida: '+k); return x.trim(); };
const numero = (v,k) => { const x=Number(v[k]?.value); if(!Number.isFinite(x)||x<=0) throw new Error('Valor positivo requerido: '+k); return x; };
const huella = c => createHash('sha256').update(c).digest('hex');

function constanciaActa(v) {
 const contenido=JSON.stringify({documento:'ACTA_ENTREGA_EQUIPO',equipo:texto(v,'equipoId'),responsable:texto(v,'responsableId'),acta:texto(v,'actaEntrega')});
 return {constanciaContenido:{value:contenido,type:'String'},constanciaHash:{value:huella(contenido),type:'String'}};
}
function factura(v) {
 const contenido=JSON.stringify({documento:'FACTURA',numero:texto(v,'numeroFactura'),venta:texto(v,'documentoId'),cliente:texto(v,'clienteId'),valor:numero(v,'valorVenta')});
 return {facturaContenido:{value:contenido,type:'String'},facturaHash:{value:huella(contenido),type:'String'},facturaEnviada:{value:true,type:'Boolean'}};
}
/** Variables que viajan con el mensaje VentaEntregada (se conservan los tipos del motor). */
function mensajeVenta(v) {
 const processVariables={};
 for(const k of VENTA){ if(v[k]?.value===undefined||v[k]?.value===null) throw new Error('Variable requerida: '+k); processVariables[k]={value:v[k].value,type:v[k].type}; }
 if(v.solicitantePortal?.value)processVariables.solicitantePortal={value:v.solicitantePortal.value,type:'String'};
 processVariables.documentoId={value:texto(v,'pedidoId'),type:'String'};
 return {messageName:'VentaEntregada',businessKey:texto(v,'pedidoId'),processVariables};
}

async function http(method,path,body){
 const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',Authorization:'Basic '+Buffer.from(user+':'+password).toString('base64')},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
 if(!r.ok)throw new Error('HTTP '+r.status+': '+await r.text());
 return r.status===204?null:r.json();
}
const post=(p,b)=>http('POST',p,b), get=p=>http('GET',p);

async function trasladarVenta(v) {
 const msg=mensajeVenta(v), bk=encodeURIComponent(msg.businessKey);
 // Idempotente: si un reintento llega después de iniciar PR-SO-05, no se crea una segunda cuenta.
 const activas=await get('/process-instance/count?processDefinitionKey=facturar_recaudar&businessKey='+bk);
 const historicas=await get('/history/process-instance/count?processDefinitionKey=facturar_recaudar&processInstanceBusinessKey='+bk);
 if(activas.count===0&&historicas.count===0) await post('/message',msg);
 return {ventaTrasladada:{value:true,type:'Boolean'}};
}
const handlers={'trasladar-venta':trasladarVenta,'constancia-acta-equipo':constanciaActa,'enviar-factura':factura};

async function run(){console.log('Trabajador externo JavaScript. Motor:',base);while(true){try{
 const jobs=await post('/external-task/fetchAndLock',{workerId,maxTasks:5,topics:Object.entries(topics).map(([topicName,variables])=>({topicName,lockDuration:30000,variables}))});
 for(const task of jobs){try{
  const variables=await handlers[task.topicName](task.variables);
  await post('/external-task/'+task.id+'/complete',{workerId,variables});
  console.log('Completada:',task.topicName,task.processInstanceId,(variables.constanciaHash||variables.facturaHash||{}).value||'');
 }catch(e){console.error(task.topicName,e.message);await post('/external-task/'+task.id+'/failure',{workerId,errorMessage:e.message.slice(0,600),retries:0,retryTimeout:0});}}
 }catch(e){console.error('Motor no disponible:',e.message);}await new Promise(r=>setTimeout(r,1000));}}
module.exports={constanciaActa,factura,mensajeVenta,topics};if(require.main===module)run();
