-- Conectarse con una herramienta JDBC a jdbc:h2:file:./data/agua-cumbre
-- Usuario sa; contraseña DB_PASSWORD (vacía en la demostración).
-- Detener la aplicación antes de abrir el archivo desde otro proceso.
SELECT id,tipo,estado,responsable,version FROM ac_equipo ORDER BY id;
SELECT id,presentacion,fabricacion,vencimiento,liberado,disponible FROM ac_lote;
SELECT id,cliente,vendedor,zona,canal,lote,cantidad,total,estado,comprobante FROM ac_pedido;
SELECT id,tipo,tercero,total,saldo,estado,gestion FROM ac_cuenta ORDER BY id;
SELECT documento,comprobante,valor FROM ac_pago ORDER BY documento;
SELECT instancia,tipo,referencia,detalle,valor,fecha FROM ac_movimiento ORDER BY id;
