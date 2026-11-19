package com.aguacumbre;
import java.time.LocalDate;
import java.util.*;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:pruebas;DB_CLOSE_DELAY=-1","camunda.bpm.job-execution.enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProcesosTest {
 @Autowired RuntimeService runtime; @Autowired TaskService tasks; @Autowired RepositoryService repository;
 @Autowired DecisionService decisions; @Autowired ExternalTaskService external; @Autowired ManagementService management;
 @Autowired JdbcTemplate db;
 @BeforeEach void limpiar() {
  for(var p:runtime.createProcessInstanceQuery().rootProcessInstances().list())
   if(runtime.createProcessInstanceQuery().processInstanceId(p.getId()).count()>0) runtime.deleteProcessInstance(p.getId(),"Prueba completada");
  for(String t:List.of("ac_pago","ac_movimiento","ac_cuenta","ac_pedido","ac_equipo","ac_lote","ac_plan"))db.update("DELETE FROM "+t);
  db.update("INSERT INTO ac_equipo(id,tipo,estado) VALUES('EQ-TEST','DISPENSADOR','DISPONIBLE')");
  lote("L-TEST",true,20,10); lote("L-PEND",false,20,10); lote("L-VENC",true,-1,10);
 }
 void lote(String id,boolean liberado,int dias,long unidades){db.update("INSERT INTO ac_lote(id,presentacion,fabricacion,vencimiento,disponible,liberado) VALUES(?,'BOTELLON',?,?,?,?)",id,LocalDate.now().minusDays(5),LocalDate.now().plusDays(dias),unidades,liberado);}
 Map<String,Object> vars(Object... kv){Map<String,Object> r=new HashMap<>();for(int i=0;i<kv.length;i+=2)r.put((String)kv[i],kv[i+1]);return r;}
 Task task(String pid,String key){return tasks.createTaskQuery().processInstanceId(pid).taskDefinitionKey(key).singleResult();}
 void done(String pid,String key,Object... kv){Task t=task(pid,key);assertThat(t).as(key).isNotNull();tasks.complete(t.getId(),vars(kv));}
 void ended(String pid){assertThat(runtime.createProcessInstanceQuery().processInstanceId(pid).count()).isZero();}
 String child(String parent){return runtime.createProcessInstanceQuery().superProcessInstanceId(parent).singleResult().getId();}
 long n(String sql,Object... a){return db.queryForObject(sql,Long.class,a);}
 void externa(String pid,String topic,Map<String,Object> v){
  String bk=runtime.createProcessInstanceQuery().processInstanceId(pid).singleResult().getBusinessKey();
  var jobs=external.fetchAndLock(10,"test").topic(topic,30000).businessKey(bk).execute();
  assertThat(jobs).as(topic).hasSize(1);external.complete(jobs.get(0).getId(),"test",v);
 }
 void mensaje(String pid,String nombre){runtime.createMessageCorrelation(nombre).processInstanceId(pid).correlate();}
 boolean espera(String pid,String nombre){return runtime.createEventSubscriptionQuery().processInstanceId(pid).eventType("message").eventName(nombre).count()==1;}
 void timer(String pid){var t=management.createJobQuery().processInstanceId(pid).timers().singleResult();assertThat(t).isNotNull();management.executeJob(t.getId());}

 // ------------------------------------------------------------- PR-CO-03
 String pedido(String id,String lote,long cantidad,String canal){
  String pid=runtime.createMessageCorrelation("PedidoRecibido").processInstanceBusinessKey(id).setVariables(vars("pedidoId",id,"clienteId","CLI-1")).correlateStartMessage().getId();
  done(pid,"registrar","vendedorId","VEN-1","zona","Centro","canal",canal,"loteId",lote,"cantidad",cantidad,"precioUnitario",100L);
  return pid;
 }
 String hastaEntrega(String id,String canal){String p=pedido(id,"L-TEST",3L,canal);done(p,"organizar","vendedorId","VEN-1","zona","Centro");return p;}
 @Test void despliegaModelosDecisionesYFormularios(){
  assertThat(repository.createProcessDefinitionQuery().latestVersion().count()).isEqualTo(7);
  assertThat(repository.createDecisionDefinitionQuery().latestVersion().count()).isEqualTo(4);
  var def=repository.createProcessDefinitionQuery().processDefinitionKey("distribuir_comercializar").latestVersion().singleResult();
  assertThat(repository.getDeploymentResourceNames(def.getDeploymentId())).contains("static/form/dist_pedido.form","static/form/fin_pago.form","static/dmn/validar_despacho.dmn");
 }
 @Test void dmnValidarDespacho(){
  Object[][] casos={{true,true,10L,"DESPACHAR"},{true,true,1L,"ESPERAR"},{true,false,10L,"BLOQUEAR"},{false,true,10L,"ESPERAR"},{false,false,10L,"BLOQUEAR"}};
  for(Object[] c:casos) assertThat((Object)decisions.evaluateDecisionTableByKey("validar_despacho",vars("loteLiberado",c[0],"loteVigente",c[1],"existencias",c[2],"cantidad",3L)).getSingleEntry()).isEqualTo(c[3]);
 }
 @Test void dmnCarteraEquipoYCobro(){
  assertThat((Object)decisions.evaluateDecisionTableByKey("estado_cartera",vars("pagoValido",true,"saldo",1L)).getSingleEntry()).isEqualTo("PENDIENTE");
  assertThat((Object)decisions.evaluateDecisionTableByKey("estado_cartera",vars("pagoValido",true,"saldo",0L)).getSingleEntry()).isEqualTo("CERRADA");
  assertThat((Object)decisions.evaluateDecisionTableByKey("estado_cartera",vars("pagoValido",false,"saldo",5L)).getSingleEntry()).isEqualTo("REVISAR");
  assertThat((Object)decisions.evaluateDecisionTableByKey("disposicion_equipo",vars("equipoApto",false)).getSingleEntry()).isEqualTo("MANTENIMIENTO");
  assertThat((Object)decisions.evaluateDecisionTableByKey("accion_cobro",vars("canal","DISTRIBUIDOR","valorVenta",50L)).getSingleEntry()).isEqualTo("ACUERDO_DE_PAGO");
 }
 @Test void ventaDirectaIniciaFacturacion(){
  String p=hastaEntrega("P-1","DIRECTO");
  done(p,"entregar","comprobanteEntrega","ENT-1");
  assertThat(n("SELECT disponible FROM ac_lote WHERE id='L-TEST'")).isEqualTo(7);
  assertThat(db.queryForObject("SELECT estado FROM ac_pedido WHERE id='P-1'",String.class)).isEqualTo("ENTREGADO");
  // simula el worker JS: envía VentaEntregada y completa la tarea externa
  runtime.createMessageCorrelation("VentaEntregada").processInstanceBusinessKey("P-1")
   .setVariables(vars("pedidoId","P-1","documentoId","P-1","clienteId","CLI-1","canal","DIRECTO","valorVenta",300L)).correlateStartMessage();
  externa(p,"trasladar-venta",vars("ventaTrasladada",true));
  ended(p);
  assertThat(runtime.createProcessInstanceQuery().processDefinitionKey("facturar_recaudar").processInstanceBusinessKey("P-1").count()).isEqualTo(1);
 }
 @Test void canalDistribuidorUsaCallActivity(){
  String p=hastaEntrega("P-2","DISTRIBUIDOR"),c=child(p);
  done(c,"recibir","envasesVacios",3L);done(c,"registrar","envasesLlenos",3L);
  assertThat(db.queryForObject("SELECT comprobante FROM ac_pedido WHERE id='P-2'",String.class)).contains("vacíos 3");
  assertThat(external.createExternalTaskQuery().processInstanceId(p).topicName("trasladar-venta").count()).isEqualTo(1);
 }
 @Test void loteNoLiberadoEsperaMensaje(){
  String p=pedido("P-3","L-PEND",3L,"DIRECTO");
  done(p,"demora","observacionSeguimiento","Se avisó al cliente");
  assertThat(espera(p,"LoteLiberado")).isTrue();
  db.update("UPDATE ac_lote SET liberado=TRUE WHERE id='L-PEND'");mensaje(p,"LoteLiberado");
  assertThat(task(p,"organizar")).isNotNull();
 }
 @Test void existenciasInsuficientesEsperan(){String p=pedido("P-4","L-TEST",50L,"DIRECTO");assertThat(task(p,"demora")).isNotNull();}
 @Test void loteVencidoSeBloquea(){
  String p=pedido("P-5","L-VENC",3L,"DIRECTO");ended(p);
  assertThat(n("SELECT COUNT(*) FROM ac_pedido")).isZero();
  assertThat(n("SELECT COUNT(*) FROM ac_movimiento WHERE tipo='LOTE_BLOQUEADO'")).isEqualTo(1);
 }
 @Test void plazoVencidoReprogramaYCancela(){
  String p=pedido("P-6","L-PEND",3L,"DIRECTO");done(p,"demora","observacionSeguimiento","Aviso");
  timer(p);assertThat(espera(p,"LoteLiberado")).isFalse();
  done(p,"reprog","reintentarStock",false);ended(p);
 }
 @Test void reintentoVuelveAConsultar(){
  String p=pedido("P-7","L-PEND",3L,"DIRECTO");done(p,"demora","observacionSeguimiento","Aviso");timer(p);
  db.update("UPDATE ac_lote SET liberado=TRUE WHERE id='L-PEND'");
  done(p,"reprog","reintentarStock",true);assertThat(task(p,"organizar")).isNotNull();
 }
 @Test void sinStockAlRegistrarSalidaActivaErrorDeBorde(){
  String p=hastaEntrega("P-8","DIRECTO");db.update("UPDATE ac_lote SET disponible=0 WHERE id='L-TEST'");
  done(p,"entregar","comprobanteEntrega","ENT-8");
  assertThat(task(p,"demora")).isNotNull();assertThat(n("SELECT COUNT(*) FROM ac_pedido")).isZero();
 }
 @Test void loteInexistenteSeRechazaEnElFormulario(){
  String pid=runtime.createMessageCorrelation("PedidoRecibido").processInstanceBusinessKey("P-9").setVariables(vars("pedidoId","P-9","clienteId","CLI-1")).correlateStartMessage().getId();
  assertThatThrownBy(()->done(pid,"registrar","vendedorId","V","zona","Z","canal","DIRECTO","loteId","NO-EXISTE","cantidad",1L,"precioUnitario",1L)).hasStackTraceContaining("Lote inexistente");
  assertThat(task(pid,"registrar")).isNotNull();
 }

 // ------------------------------------------------------------- PR-SO-04
 String solicitud(){return runtime.createMessageCorrelation("SolicitudEquipo").processInstanceBusinessKey("EQ-TEST").setVariables(vars("equipoId","EQ-TEST","responsableId","CLI-1")).correlateStartMessage().getId();}
 String eqEstado(){return db.queryForObject("SELECT estado FROM ac_equipo WHERE id='EQ-TEST'",String.class);}
 String asignado(){String p=solicitud();done(p,"asignar","actaEntrega","ACTA-1");externa(p,"constancia-acta-equipo",vars("constanciaHash","H"));return p;}
 @Test void asignaYEsperaDevolucion(){String p=asignado();assertThat(eqEstado()).isEqualTo("ASIGNADO");assertThat(espera(p,"DevolucionEquipo")).isTrue();}
 @Test void devolucionAptaReintegra(){
  String p=asignado();mensaje(p,"DevolucionEquipo");
  done(p,"estado","actaDevolucion","DEV-1","equipoApto",true);ended(p);
  assertThat(eqEstado()).isEqualTo("DISPONIBLE");
 }
 @Test void devolucionNoAptaPasaPorMantenimiento(){
  String p=asignado();mensaje(p,"DevolucionEquipo");done(p,"estado","actaDevolucion","DEV-1","equipoApto",false);
  String c=child(p);assertThat(eqEstado()).isEqualTo("MANTENIMIENTO");
  done(c,"diag","diagnostico","Válvula dañada");done(c,"reg","informeMantenimiento","TEC-1","equipoApto",true);
  ended(p);assertThat(eqEstado()).isEqualTo("DISPONIBLE");
 }
 @Test void vencimientoDeLaAsignacionRecuperaElEquipo(){String p=asignado();timer(p);assertThat(task(p,"estado")).isNotNull();}
 @Test void equipoNoDisponibleSeInforma(){
  db.update("UPDATE ac_equipo SET estado='ASIGNADO',responsable='OTRO'");String p=solicitud();
  done(p,"informar","observacionSeguimiento","Sin equipos");ended(p);assertThat(eqEstado()).isEqualTo("ASIGNADO");
 }

 // ------------------------------------------------------------- PR-SO-05
 String venta(){
  String p=runtime.createMessageCorrelation("VentaEntregada").processInstanceBusinessKey("V-1")
   .setVariables(vars("pedidoId","V-1","documentoId","V-1","clienteId","CLI-1","canal","DIRECTO","valorVenta",100L)).correlateStartMessage().getId();
  done(child(p),"soporte_revisar","soporteReferencia","F-AC-PT-002","observacionesSoporte","Salida verificada");
  externa(p,"enviar-factura",vars("facturaEnviada",true));
  assertThat(espera(p,"PagoRecibido")).isTrue();
  return p;
 }
 long saldo(){return n("SELECT saldo FROM ac_cuenta WHERE id='V-1'");}
 @Test void abonosHastaCerrarLaCuenta(){
  String p=venta();assertThat(db.queryForObject("SELECT estado FROM ac_cuenta WHERE id='V-1'",String.class)).isEqualTo("POR_COBRAR");
  mensaje(p,"PagoRecibido");done(p,"regpago","pago",40L,"comprobantePago","R-1");
  assertThat(saldo()).isEqualTo(60);assertThat(espera(p,"PagoRecibido")).isTrue();
  mensaje(p,"PagoRecibido");done(p,"regpago","pago",60L,"comprobantePago","R-2");
  done(p,"reporte","observacionesReporte","Cuenta saldada");ended(p);
  assertThat(db.queryForObject("SELECT estado FROM ac_cuenta WHERE id='V-1'",String.class)).isEqualTo("CERRADA");
 }
 @Test void sobrepagoVaARevision(){
  String p=venta();mensaje(p,"PagoRecibido");done(p,"regpago","pago",101L,"comprobantePago","R-X");
  assertThat(saldo()).isEqualTo(100);done(p,"corregir","observacionSeguimiento","Valor corregido");
  assertThat(task(p,"regpago")).isNotNull();
 }
 @Test void comprobanteRepetidoNoAfectaSaldo(){
  String p=venta();mensaje(p,"PagoRecibido");done(p,"regpago","pago",40L,"comprobantePago","R-1");
  mensaje(p,"PagoRecibido");done(p,"regpago","pago",40L,"comprobantePago","R-1");
  assertThat(saldo()).isEqualTo(60);assertThat(task(p,"corregir")).isNotNull();
 }
 @Test void plazoDePagoVencidoGestionaCobro(){
  String p=venta();timer(p);
  assertThat(runtime.getVariable(p,"accionCobro")).isEqualTo("RECORDATORIO");
  done(p,"cobro","observacionSeguimiento","Llamada al cliente");
  assertThat(espera(p,"PagoRecibido")).isTrue();
 }
}
