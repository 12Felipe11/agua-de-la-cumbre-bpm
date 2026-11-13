const { test } = require('node:test'); const assert = require('node:assert/strict');
const { handlers, topics, lotes, equipos, ErrorBpmn } = require('./worker.cjs');
const V = o => Object.fromEntries(Object.entries(o).map(([k, value]) => [k, { value }]));
const T = (activityId, processInstanceId = 'PI-1') => ({ activityId, processInstanceId });
test('13 topics declarados', () => assert.equal(topics.length, 13));
test('consulta de lote: liberado, vencido y pendiente', () => {
  const base = { pedidoId: 'P', cantidad: 2, precioUnitario: 10000 };
  const d = handlers['consultar-inventario'](V({ ...base, loteId: 'LOTE-DEMO' }));
  assert.equal(d.loteLiberado.value, true); assert.equal(d.loteVigente.value, true); assert.equal(d.valorVenta.value, 20000); assert.ok(d.plazoEsperaLote);
  assert.equal(handlers['consultar-inventario'](V({ ...base, loteId: 'LOTE-VENCIDO' })).loteVigente.value, false);
  assert.equal(handlers['consultar-inventario'](V({ ...base, loteId: 'LOTE-PENDIENTE' })).loteLiberado.value, false);
  assert.throws(() => handlers['consultar-inventario'](V({ ...base, loteId: 'NO' })));
});
test('salida sin existencias lanza error BPMN SIN_STOCK', () => {
  const v = { pedidoId: 'P', clienteId: 'C', zona: 'Z', canal: 'DIRECTO', comprobanteEntrega: 'E', loteId: 'LOTE-DEMO' };
  const antes = lotes['LOTE-DEMO'].disponible;
  handlers['registrar-salida-producto'](V({ ...v, cantidad: 2 })); assert.equal(lotes['LOTE-DEMO'].disponible, antes - 2);
  assert.throws(() => handlers['registrar-salida-producto'](V({ ...v, cantidad: 999999 })), e => e instanceof ErrorBpmn && e.code === 'SIN_STOCK');
});
test('equipos: asignar, mantenimiento y reintegro', () => {
  const v = V({ equipoId: 'EQ-002', responsableId: 'VEN-1' });
  assert.equal(handlers['consultar-inventario-equipos'](v).equipoDisponible.value, true);
  handlers['actualizar-inventario-equipos'](v, T('registrar')); assert.equal(equipos['EQ-002'], 'ASIGNADO');
  assert.equal(handlers['consultar-inventario-equipos'](v).equipoDisponible.value, false);
  handlers['actualizar-inventario-equipos'](v, T('marcar')); assert.equal(equipos['EQ-002'], 'MANTENIMIENTO');
  handlers['actualizar-inventario-equipos'](v, T('reintegrar')); assert.equal(equipos['EQ-002'], 'DISPONIBLE');
});
test('cartera: abonos, comprobante repetido y cierre', () => {
  const r = handlers['registrar-venta'](V({ pedidoId: 'V-1', clienteId: 'C', valorVenta: 100 }), T('registrar', 'SO5-1'));
  const base = { cuentaId: r.cuentaId.value, documentoId: 'V-1' };
  assert.equal(handlers['generar-factura'](V(base)).saldo.value, 100);
  assert.equal(handlers['validar-pago'](V({ pago: 101, saldo: 100, comprobantePago: 'R' })).pagoValido.value, false);
  assert.equal(handlers['aplicar-abono'](V({ ...base, pagoValido: true, pago: 40, comprobantePago: 'R1' })).saldo.value, 60);
  assert.equal(handlers['aplicar-abono'](V({ ...base, pagoValido: true, pago: 40, comprobantePago: 'R1' })).pagoValido.value, false);
  assert.equal(handlers['aplicar-abono'](V({ ...base, pagoValido: true, pago: 60, comprobantePago: 'R2' })).saldo.value, 0);
});
test('constancia y factura con huella SHA-256', () => {
  assert.match(handlers['constancia-acta-equipo'](V({ equipoId: 'E', responsableId: 'R', actaEntrega: 'A' })).constanciaHash.value, /^[a-f0-9]{64}$/);
  assert.match(handlers['enviar-factura'](V({ numeroFactura: 'F', documentoId: 'D', clienteId: 'C', valorVenta: 5 })).facturaHash.value, /^[a-f0-9]{64}$/);
});
