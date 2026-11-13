// Trabajador de External Tasks (JavaScript) para PR-CO-03, PR-SO-04 y PR-SO-05.
//   node worker.cjs
// - Atiende las 13 tareas externas de los procesos (inventario, salidas, equipos, factura, cartera).
// - Abre http://localhost:3000 con un panel para enviar los mensajes que esperan las instancias
//   (lote liberado, devolución de equipo, pago del cliente).
// Los inventarios y la cartera son SIMULADOS en memoria (se reinician al reiniciar el worker).
const { createHash } = require('node:crypto');
const { base, get, post } = require('./camunda.cjs');
const workerId = 'agua-cumbre-worker';

// ---- Plazos de los eventos de tiempo (valores cortos para la demostración) ----
const PLAZOS = {
  plazoEsperaLote: process.env.PLAZO_ESPERA_LOTE || 'PT1M',   // operación real propuesta: P3D
  plazoAsignacion: process.env.PLAZO_ASIGNACION || 'PT2M',    // operación real propuesta: P30D
  plazoPago: process.env.PLAZO_PAGO || 'PT1M',                // operación real propuesta: P30D
};

// ---- Datos simulados ----
const dias = n => new Date(Date.now() + n * 86400000);
const lotes = {
  'LOTE-DEMO':      { liberado: true,  vence: dias(30), disponible: 1000 },  // se despacha
  'LOTE-PENDIENTE': { liberado: false, vence: dias(30), disponible: 50 },    // espera liberación (PR-CO-02)
  'LOTE-VENCIDO':   { liberado: true,  vence: dias(-1), disponible: 30 },    // se bloquea (PR-CO-04)
};
const equipos = { 'EQ-001': 'DISPONIBLE', 'EQ-002': 'DISPONIBLE', 'EQ-003': 'DISPONIBLE' };
const cuentas = {};               // cuentaId (instancia de PR-SO-05) -> { saldo, comprobantes:Set }
const cuenta = v => { const c = cuentas[texto(v, 'cuentaId')]; if (!c) throw new Error('Cuenta no registrada en el worker (¿se reinició?)'); return c; };
const movimientos = [];
const registrar = (tipo, ref, detalle) => { movimientos.unshift({ fecha: new Date().toISOString(), tipo, ref, detalle }); movimientos.length = Math.min(movimientos.length, 50); console.log(tipo, ref, detalle); };

// ---- Utilidades ----
const val = (v, k) => v[k]?.value;
const texto = (v, k) => { const x = val(v, k); if (typeof x !== 'string' || !x.trim()) throw new Error('Variable requerida: ' + k); return x.trim(); };
const numero = (v, k) => { const x = Number(val(v, k)); if (!Number.isFinite(x) || x <= 0) throw new Error('Valor positivo requerido: ' + k); return Math.round(x); };
const S = value => ({ value, type: 'String' }), B = value => ({ value, type: 'Boolean' }), L = value => ({ value: Math.round(value), type: 'Long' });
const huella = c => createHash('sha256').update(c).digest('hex');
class ErrorBpmn extends Error { constructor(code, msg) { super(msg); this.code = code; } }

// ================== Lógica por topic: (variables, tarea) -> variables a devolver ==================
const handlers = {
  // ---------- PR-CO-03 ----------
  'consultar-inventario': (v) => {
    const lote = lotes[texto(v, 'loteId')];
    if (!lote) throw new Error('Lote inexistente: ' + val(v, 'loteId') + ' (use LOTE-DEMO, LOTE-PENDIENTE o LOTE-VENCIDO)');
    const r = {
      loteLiberado: B(lote.liberado), loteVigente: B(lote.vence >= new Date()), existencias: L(lote.disponible),
      valorVenta: L(numero(v, 'cantidad') * numero(v, 'precioUnitario')), documentoId: S(texto(v, 'pedidoId')),
    };
    if (!val(v, 'plazoEsperaLote')) r.plazoEsperaLote = S(PLAZOS.plazoEsperaLote);
    return r;
  },
  'bloquear-lote': (v) => { registrar('LOTE_BLOQUEADO', texto(v, 'loteId'), 'Lote vencido; remitido a PR-CO-04. Pedido ' + texto(v, 'pedidoId')); return { loteBloqueado: B(true) }; },
  'registrar-salida-producto': (v) => {
    const id = texto(v, 'loteId'), lote = lotes[id], cant = numero(v, 'cantidad');
    if (!lote || !lote.liberado || lote.vence < new Date() || lote.disponible < cant) throw new ErrorBpmn('SIN_STOCK', 'Sin existencias liberadas y vigentes del lote ' + id);
    lote.disponible -= cant;
    const comprobante = val(v, 'canal') === 'DIRECTO' ? texto(v, 'comprobanteEntrega') : `Intercambio: vacíos ${numero(v, 'envasesVacios')}, llenos ${numero(v, 'envasesLlenos')}`;
    registrar('SALIDA F-AC-PT-002', texto(v, 'pedidoId'), `cliente ${texto(v, 'clienteId')}, zona ${texto(v, 'zona')}, lote ${id}, unidades ${cant}; ${comprobante}`);
    return { salidaRegistrada: B(true) };
  },
  'trasladar-venta': async (v, task) => {
    // Idempotente: solo inicia PR-SO-05 si esta instancia de PR-CO-03 aún no lo hizo.
    const filtro = 'origenInstancia_eq_' + task.processInstanceId;
    const a = await get('/process-instance/count?processDefinitionKey=facturar_recaudar&variables=' + filtro);
    const h = await get('/history/process-instance/count?processDefinitionKey=facturar_recaudar&variables=' + filtro);
    if (a.count === 0 && h.count === 0) {
      await post('/message', { messageName: 'VentaEntregada', businessKey: texto(v, 'pedidoId'), processVariables: {
        pedidoId: S(texto(v, 'pedidoId')), documentoId: S(texto(v, 'pedidoId')), clienteId: S(texto(v, 'clienteId')),
        canal: S(texto(v, 'canal')), valorVenta: L(numero(v, 'valorVenta')), origenInstancia: S(task.processInstanceId) } });
    }
    return { ventaTrasladada: B(true) };
  },
  // ---------- PR-SO-04 ----------
  'consultar-inventario-equipos': (v) => {
    const id = texto(v, 'equipoId');
    if (!(id in equipos)) throw new Error('Equipo inexistente: ' + id + ' (use EQ-001, EQ-002 o EQ-003)');
    const r = { equipoDisponible: B(equipos[id] === 'DISPONIBLE') };
    if (!val(v, 'plazoAsignacion')) r.plazoAsignacion = S(PLAZOS.plazoAsignacion);
    return r;
  },
  'constancia-acta-equipo': (v) => {
    const contenido = JSON.stringify({ documento: 'ACTA_ENTREGA_EQUIPO', equipo: texto(v, 'equipoId'), responsable: texto(v, 'responsableId'), acta: texto(v, 'actaEntrega') });
    return { constanciaContenido: S(contenido), constanciaHash: S(huella(contenido)) };
  },
  'actualizar-inventario-equipos': (v, task) => {
    const id = texto(v, 'equipoId');
    const nuevo = { registrar: 'ASIGNADO', marcar: 'MANTENIMIENTO', reintegrar: 'DISPONIBLE' }[task.activityId];
    if (!nuevo) throw new Error('Actividad no reconocida: ' + task.activityId);
    if (nuevo === 'ASIGNADO' && equipos[id] !== 'DISPONIBLE') throw new Error('El equipo ' + id + ' ya no está disponible');
    equipos[id] = nuevo;
    registrar('EQUIPO_' + nuevo, id, 'Responsable: ' + texto(v, 'responsableId'));
    return { estadoInventario: S(nuevo) };
  },
  // ---------- PR-SO-05 ----------
  'registrar-venta': (v, task) => {
    const doc = texto(v, 'pedidoId');
    cuentas[task.processInstanceId] = { documento: doc, cliente: texto(v, 'clienteId'), saldo: numero(v, 'valorVenta'), comprobantes: new Set(), estado: 'REGISTRADA' };
    registrar('VENTA_REGISTRADA', doc, 'Cliente ' + texto(v, 'clienteId') + '; valor ' + numero(v, 'valorVenta'));
    return { documentoId: S(doc), cuentaId: S(task.processInstanceId) };
  },
  'generar-factura': (v) => {
    const c = cuenta(v); c.estado = 'FACTURADA';
    const r = { numeroFactura: S('FV-' + texto(v, 'documentoId')), tipoOperacion: S('VENTA'), saldo: L(c.saldo) };
    if (!val(v, 'plazoPago')) r.plazoPago = S(PLAZOS.plazoPago);
    return r;
  },
  'registrar-cuenta-por-cobrar': (v) => { cuenta(v).estado = 'POR_COBRAR'; registrar('CUENTA_POR_COBRAR', texto(v, 'documentoId'), 'Saldo ' + numero(v, 'saldo')); return { cuentaPorCobrar: B(true) }; },
  'enviar-factura': (v) => {
    const contenido = JSON.stringify({ documento: 'FACTURA', numero: texto(v, 'numeroFactura'), venta: texto(v, 'documentoId'), cliente: texto(v, 'clienteId'), valor: numero(v, 'valorVenta') });
    return { facturaContenido: S(contenido), facturaHash: S(huella(contenido)), facturaEnviada: B(true) };
  },
  'validar-pago': (v) => {
    const pago = Number(val(v, 'pago')), saldo = Number(val(v, 'saldo')), comp = val(v, 'comprobantePago');
    return { pagoValido: B(pago > 0 && pago <= saldo && typeof comp === 'string' && comp.trim() !== '') };
  },
  'aplicar-abono': (v) => {
    // Corre en el subproceso conciliar_cartera; cuentaId llega del padre (camunda:in variables="all").
    if (!val(v, 'pagoValido')) return {};
    const c = cuenta(v);
    const comp = texto(v, 'comprobantePago');
    if (c.comprobantes.has(comp)) { registrar('PAGO_RECHAZADO', c.documento, 'Comprobante repetido ' + comp); return { pagoValido: B(false) }; }
    c.comprobantes.add(comp); c.saldo -= numero(v, 'pago'); c.estado = c.saldo === 0 ? 'CERRADA' : 'POR_COBRAR';
    registrar('PAGO', c.documento, `Comprobante ${comp}; nuevo saldo ${c.saldo}`);
    return { saldo: L(c.saldo) };
  },
};
const topics = Object.keys(handlers);

async function run() {
  console.log('Worker JS de Agua de la Cumbre. Motor:', base, '| topics:', topics.length);
  for (;;) {
    try {
      const jobs = await post('/external-task/fetchAndLock', { workerId, maxTasks: 10, topics: topics.map(topicName => ({ topicName, lockDuration: 30000 })) });
      for (const task of jobs) {
        try {
          const variables = await handlers[task.topicName](task.variables || {}, task);
          await post(`/external-task/${task.id}/complete`, { workerId, variables });
          console.log('✓', task.topicName, task.processInstanceId);
        } catch (e) {
          if (e instanceof ErrorBpmn) { await post(`/external-task/${task.id}/bpmnError`, { workerId, errorCode: e.code, errorMessage: e.message }); console.log('⚑ error BPMN', e.code, e.message); }
          else { console.error('✗', task.topicName, e.message); await post(`/external-task/${task.id}/failure`, { workerId, errorMessage: e.message.slice(0, 600), retries: 0, retryTimeout: 0 }); }
        }
      }
    } catch (e) { console.error('Motor no disponible:', e.message); }
    await new Promise(r => setTimeout(r, 1000));
  }
}

// ================== Panel de eventos externos (http://localhost:3000) ==================
const EVENTOS = { LoteLiberado: 'Liberar lote (simula PR-CO-02)', DevolucionEquipo: 'Solicitar devolución del equipo', PagoRecibido: 'Registrar pago del cliente' };
const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
async function panel() {
  const subs = (await get('/event-subscription?eventType=message')).filter(s => s.processInstanceId && EVENTOS[s.eventName]);
  const filas = subs.map(s => `<tr><td>${esc(s.eventName)}</td><td><code>${esc(s.processInstanceId)}</code></td><td><form method="post" action="/enviar/${esc(s.id)}"><button>${esc(EVENTOS[s.eventName])}</button></form></td></tr>`).join('');
  const inv = Object.entries(lotes).map(([k, l]) => `${k}: ${l.liberado ? 'liberado' : 'sin liberar'}, ${l.vence >= new Date() ? 'vigente' : 'vencido'}, ${l.disponible} u.`).join('<br>');
  const eq = Object.entries(equipos).map(([k, e]) => `${k}: ${e}`).join('<br>');
  const cu = Object.values(cuentas).map(c => `${esc(c.documento)}: saldo ${c.saldo} (${c.estado})`).join('<br>') || '—';
  const mv = movimientos.map(m => `<tr><td>${esc(m.fecha.slice(11, 19))}</td><td>${esc(m.tipo)}</td><td>${esc(m.ref)}</td><td>${esc(m.detalle)}</td></tr>`).join('');
  return `<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="5"><title>Eventos externos · Agua de la Cumbre</title>
<style>body{font:15px system-ui;margin:24px;color:#19324d}h1{color:#024a96}table{border-collapse:collapse;width:100%;margin:8px 0 24px}td,th{border-bottom:1px solid #dce6f1;padding:8px;text-align:left}button{background:#024a96;color:#fff;border:0;border-radius:6px;padding:8px 12px;cursor:pointer}.g{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.c{background:#edf5fc;padding:12px;border-radius:8px}</style>
<h1>Agua de la Cumbre · eventos externos</h1><p>Instancias que esperan un mensaje. La página se actualiza cada 5 s. Motor: ${esc(base)}</p>
<table><tr><th>Mensaje esperado</th><th>Instancia</th><th>Acción</th></tr>${filas || '<tr><td colspan="3">No hay instancias esperando mensajes.</td></tr>'}</table>
<div class="g"><div class="c"><b>Lotes</b><br>${inv}</div><div class="c"><b>Equipos</b><br>${eq}</div><div class="c"><b>Cuentas</b><br>${cu}</div></div>
<h2>Movimientos</h2><table>${mv}</table>`;
}
async function enviar(id) {
  const s = await get('/event-subscription?eventType=message').then(l => l.find(x => x.id === id));
  if (!s) throw new Error('La espera ya no está activa');
  if (s.eventName === 'LoteLiberado') {
    const v = await get(`/process-instance/${s.processInstanceId}/variables/loteId`).catch(() => null);
    if (v && lotes[v.value]) { lotes[v.value].liberado = true; registrar('LOTE_LIBERADO', v.value, 'Simulación de PR-CO-02'); }
  }
  await post('/message', { messageName: s.eventName, processInstanceId: s.processInstanceId });
}
function servidor() {
  const port = Number(process.env.PORT || 3000);
  require('node:http').createServer(async (req, res) => {
    try {
      if (req.method === 'POST' && req.url.startsWith('/enviar/')) { await enviar(decodeURIComponent(req.url.slice(8))); res.writeHead(303, { Location: '/' }); return res.end(); }
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); res.end(await panel());
    } catch (e) { res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' }); res.end(e.message); }
  }).listen(port, '127.0.0.1', () => console.log(`Panel de eventos externos: http://localhost:${port}`));
}

module.exports = { handlers, topics, lotes, equipos, cuentas, ErrorBpmn };
if (require.main === module) { servidor(); run(); }
