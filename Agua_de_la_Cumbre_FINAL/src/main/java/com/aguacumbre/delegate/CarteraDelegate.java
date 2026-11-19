package com.aguacumbre.delegate;
import static com.aguacumbre.service.Variables.*;
import com.aguacumbre.service.RegistroService;
import org.camunda.bpm.engine.delegate.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** PR-SO-05: registro de la venta, factura, cuenta por cobrar y abonos. */
@Component("carteraDelegate")
public class CarteraDelegate implements JavaDelegate {
 private final JdbcTemplate db; private final RegistroService registro; private final String plazoPago;
 public CarteraDelegate(JdbcTemplate db,RegistroService registro,@Value("${agua.plazos.pago:PT1M}") String plazoPago) {
  this.db=db;this.registro=registro;this.plazoPago=plazoPago;
 }
 @Override public void execute(DelegateExecution e) {
  String accion=texto(e,"accion"), doc=texto(e,"documentoId");
  switch(accion) {
   case "registrarVenta" -> {
    String cliente=texto(e,"clienteId"); long total=positivo(e,"valorVenta");
    if(db.queryForObject("SELECT COUNT(*) FROM ac_cuenta WHERE id=?",Integer.class,doc)>0)
     throw new IllegalArgumentException("La venta "+doc+" ya tiene una cuenta registrada");
    db.update("INSERT INTO ac_cuenta(id,tipo,tercero,total,saldo,estado,gestion) VALUES(?,'VENTA',?,?,?,'REGISTRADA',?)",doc,cliente,total,total,e.getProcessInstanceId());
    registro.registrar(e,"VENTA_REGISTRADA",doc,"Cliente: "+cliente+"; canal: "+e.getVariable("canal"),total);
   }
   case "factura" -> {
    String numero="FV-"+doc;
    e.setVariable("numeroFactura",numero);
    e.setVariable("tipoOperacion","VENTA");
    e.setVariable("saldo",db.queryForObject("SELECT saldo FROM ac_cuenta WHERE id=?",Long.class,doc));
    if(e.getVariable("plazoPago")==null) e.setVariable("plazoPago",plazoPago);
    db.update("UPDATE ac_cuenta SET estado='FACTURADA' WHERE id=?",doc);
    registro.registrar(e,"FACTURA",doc,"Factura "+numero,0);
   }
   case "cxc" -> {
    db.update("UPDATE ac_cuenta SET estado='POR_COBRAR' WHERE id=?",doc);
    registro.registrar(e,"CUENTA_POR_COBRAR",doc,"Plazo de pago: "+e.getVariable("plazoPago"),((Number)e.getVariable("saldo")).longValue());
   }
   case "abono" -> abono(e,doc);
   default -> throw new IllegalArgumentException("Acción de cartera no implementada: "+accion);
  }
 }
 private void abono(DelegateExecution e,String doc) {
  if(!Boolean.TRUE.equals(e.getVariable("pagoValido"))) return;
  String comprobante=texto(e,"comprobantePago"); long pago=positivo(e,"pago");
  long saldo=((Number)e.getVariable("saldo")).longValue();
  if(db.queryForObject("SELECT COUNT(*) FROM ac_pago WHERE documento=? AND comprobante=?",Integer.class,doc,comprobante)>0) {
   e.setVariable("pagoValido",false);
   registro.registrar(e,"PAGO_RECHAZADO",doc,"Comprobante repetido: "+comprobante,0);
   return;
  }
  db.update("INSERT INTO ac_pago(documento,comprobante,valor) VALUES(?,?,?)",doc,comprobante,pago);
  if(db.update("UPDATE ac_cuenta SET saldo=saldo-?,estado=CASE WHEN saldo-?=0 THEN 'CERRADA' ELSE 'POR_COBRAR' END WHERE id=? AND saldo=?",pago,pago,doc,saldo)!=1)
   throw new IllegalStateException("El saldo de "+doc+" cambió; intente de nuevo");
  e.setVariable("saldo",saldo-pago);
  registro.registrar(e,"PAGO",doc,"Comprobante: "+comprobante,pago);
 }
}
