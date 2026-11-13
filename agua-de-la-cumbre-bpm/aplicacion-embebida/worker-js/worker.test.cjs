const {test}=require('node:test');const assert=require('node:assert/strict');
const {constanciaActa,factura,mensajeVenta,topics}=require('./worker.cjs');
test('constancia del acta de entrega',()=>{
 const v={equipoId:{value:'EQ-001'},responsableId:{value:'VEN-01'},actaEntrega:{value:'ACTA-1'}};
 const r=constanciaActa(v);assert.match(r.constanciaHash.value,/^[a-f0-9]{64}$/);assert.equal(r.constanciaHash.value,constanciaActa(v).constanciaHash.value);
 assert.throws(()=>constanciaActa({}));
});
test('factura con huella',()=>{
 const v={numeroFactura:{value:'FV-P1'},documentoId:{value:'P1'},clienteId:{value:'CLI-1'},valorVenta:{value:20000}};
 const r=factura(v);assert.match(r.facturaHash.value,/^[a-f0-9]{64}$/);assert.equal(r.facturaEnviada.value,true);
 assert.throws(()=>factura({...v,valorVenta:{value:0}}));
});
test('mensaje VentaEntregada conserva tipos y usa el pedido como llave',()=>{
 const v={pedidoId:{value:'P1',type:'String'},clienteId:{value:'C',type:'String'},canal:{value:'DIRECTO',type:'String'},valorVenta:{value:200,type:'Long'},loteId:{value:'L',type:'String'},cantidad:{value:2,type:'Long'},zona:{value:'Centro',type:'String'}};
 const m=mensajeVenta(v);assert.equal(m.messageName,'VentaEntregada');assert.equal(m.businessKey,'P1');
 assert.deepEqual(m.processVariables.valorVenta,{value:200,type:'Long'});assert.equal(m.processVariables.documentoId.value,'P1');
 assert.throws(()=>mensajeVenta({pedidoId:{value:'P1'}}));
});
test('topics declarados',()=>assert.deepEqual(Object.keys(topics).sort(),['constancia-acta-equipo','enviar-factura','trasladar-venta']));
