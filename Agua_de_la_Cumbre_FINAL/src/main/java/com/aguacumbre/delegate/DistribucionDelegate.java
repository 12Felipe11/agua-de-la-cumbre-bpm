package com.aguacumbre.delegate;
import static com.aguacumbre.service.Variables.*;
import com.aguacumbre.service.RegistroService;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.camunda.bpm.engine.delegate.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** PR-CO-03: consulta de lote, bloqueo de lote vencido y salida F-AC-PT-002. */
@Component("distribucionDelegate")
public class DistribucionDelegate implements JavaDelegate {
 private final JdbcTemplate db; private final RegistroService registro; private final String plazoEsperaLote;
 public DistribucionDelegate(JdbcTemplate db,RegistroService registro,@Value("${agua.plazos.espera-lote:PT1M}") String plazoEsperaLote) {
  this.db=db;this.registro=registro;this.plazoEsperaLote=plazoEsperaLote;
 }
 @Override public void execute(DelegateExecution e) {
  String accion=texto(e,"accion"), pedido=texto(e,"pedidoId"), lote=texto(e,"loteId");
  long cantidad=positivo(e,"cantidad");
  switch(accion) {
   case "consultar" -> consultar(e,pedido,lote,cantidad);
   case "bloquear" -> registro.registrar(e,"LOTE_BLOQUEADO",lote,"Pedido "+pedido+": lote vencido, no se despacha; remitido a PR-CO-04",0);
   case "salida" -> salida(e,pedido,lote,cantidad);
   default -> throw new IllegalArgumentException("Acción de distribución no implementada: "+accion);
  }
 }
 private void consultar(DelegateExecution e,String pedido,String lote,long cantidad) {
  texto(e,"clienteId");texto(e,"vendedorId");texto(e,"zona");
  String canal=texto(e,"canal");
  if(!Set.of("DIRECTO","DISTRIBUIDOR").contains(canal)) throw new IllegalArgumentException("Canal inválido");
  long precio=positivo(e,"precioUnitario");
  if(db.queryForObject("SELECT COUNT(*) FROM ac_pedido WHERE id=?",Integer.class,pedido)>0) throw new IllegalArgumentException("El pedido ya fue despachado: "+pedido);
  var rows=db.queryForList("SELECT liberado,vencimiento,disponible FROM ac_lote WHERE id=?",lote);
  if(rows.isEmpty()) throw new IllegalArgumentException("Lote inexistente: "+lote);
  Map<String,Object> l=rows.get(0);
  LocalDate vence=((Date)l.get("VENCIMIENTO")).toLocalDate();
  e.setVariable("loteLiberado",Boolean.TRUE.equals(l.get("LIBERADO")));
  e.setVariable("loteVigente",!vence.isBefore(LocalDate.now()));
  e.setVariable("fechaVencimiento",vence.toString());
  e.setVariable("existencias",((Number)l.get("DISPONIBLE")).longValue());
  e.setVariable("valorVenta",Math.multiplyExact(cantidad,precio));
  e.setVariable("documentoId",pedido);
  if(e.getVariable("plazoEsperaLote")==null) e.setVariable("plazoEsperaLote",plazoEsperaLote);
 }
 private void salida(DelegateExecution e,String pedido,String lote,long cantidad) {
  String cliente=texto(e,"clienteId"), vendedor=texto(e,"vendedorId"), zona=texto(e,"zona"), canal=texto(e,"canal");
  long total=positivo(e,"valorVenta");
  int n=db.update("UPDATE ac_lote SET disponible=disponible-? WHERE id=? AND disponible>=? AND liberado=TRUE AND vencimiento>=CURRENT_DATE",cantidad,lote,cantidad);
  if(n!=1) throw new BpmnError("SIN_STOCK","Sin existencias liberadas y vigentes suficientes del lote "+lote);
  String comprobante="DIRECTO".equals(canal)?texto(e,"comprobanteEntrega")
   :"Intercambio de envases: vacíos "+positivo(e,"envasesVacios")+", llenos "+positivo(e,"envasesLlenos");
  db.update("INSERT INTO ac_pedido(id,cliente,vendedor,zona,lote,cantidad,total,estado,comprobante,canal) VALUES(?,?,?,?,?,?,?,'ENTREGADO',?,?)",
   pedido,cliente,vendedor,zona,lote,cantidad,total,comprobante,canal);
  registro.registrar(e,"SALIDA",pedido,"F-AC-PT-002; cliente: "+cliente+"; zona: "+zona+"; lote: "+lote+"; unidades: "+cantidad+"; "+comprobante,total);
 }
}
