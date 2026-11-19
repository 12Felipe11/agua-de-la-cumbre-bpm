package com.aguacumbre.web;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.task.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class PortalService {
 private final RuntimeService runtime;private final TaskService tasks;private final IdentityService identity;private final PortalCatalog catalog;private final JdbcTemplate db;private final PortalAccess access;
 public PortalService(RuntimeService r,TaskService t,IdentityService i,PortalCatalog c,JdbcTemplate db,PortalAccess access){runtime=r;tasks=t;identity=i;catalog=c;this.db=db;this.access=access;}
 private void auth(String user){identity.setAuthentication(user,identity.createGroupQuery().groupMember(user).list().stream().map(g->g.getId()).toList());}
 /** Los tres procesos inician con un evento de mensaje: el portal envía ese mensaje con la llave de negocio. */
 @Transactional public String iniciar(String key,Map<String,String> input,String user){auth(user);try{
  access.requireStart(user,key);
  var p=catalog.proceso(key);var vars=catalog.convertir(p.formulario(),input);
  vars.put("solicitantePortal",user);
  String llave=String.valueOf(vars.get(p.llave()));
  if(runtime.createProcessInstanceQuery().processDefinitionKey(key).processInstanceBusinessKey(llave).count()>0)
   throw new IllegalArgumentException("Ya hay una instancia activa para "+llave);
  if(key.equals("distribuir_comercializar")&&db.queryForObject("SELECT COUNT(*) FROM ac_pedido WHERE id=?",Integer.class,llave)>0)
   throw new IllegalArgumentException("El pedido "+llave+" ya existe");
  if(key.equals("facturar_recaudar")){
   if(db.queryForObject("SELECT COUNT(*) FROM ac_cuenta WHERE id=?",Integer.class,llave)>0)throw new IllegalArgumentException("La venta "+llave+" ya tiene cuenta");
   vars.put("documentoId",llave);
  }
  return runtime.createMessageCorrelation(p.mensaje()).processInstanceBusinessKey(llave).setVariables(vars).correlateStartMessage().getId();
 }finally{identity.clearAuthentication();}}
 @Transactional public void completar(String id,Map<String,String> input,String user){try{
  Task task=tasks.createTaskQuery().taskId(id).singleResult();if(task==null)throw new IllegalArgumentException("Tarea finalizada o inexistente");
  access.requireWork(user,task);
  // El portal ya comprobó el grupo candidato. La operación del motor usa la
  // cuenta de servicio para que los roles de negocio no necesiten REST/Cockpit.
  auth("admin");
  if(task.getAssignee()!=null&&!task.getAssignee().equals(user))throw new IllegalArgumentException("La tarea tiene otro responsable");
  var vars=catalog.convertir(catalog.formularioTarea(task),input);
  vars.put("ultimoOperadorPortalUsuario",user);
  if(task.getAssignee()==null)tasks.claim(id,user);tasks.complete(id,vars);
 }finally{identity.clearAuthentication();}}
 /** Envía a una instancia concreta el mensaje que está esperando (lote liberado, devolución o pago). */
 @Transactional public void evento(String subscription,String user){try{
  var s=runtime.createEventSubscriptionQuery().eventSubscriptionId(subscription).eventType("message").singleResult();
  if(s==null||s.getProcessInstanceId()==null||!catalog.eventos.containsKey(s.getEventName()))throw new IllegalArgumentException("La espera ya no está activa; actualice el portal");
  access.requireEvent(user,s.getEventName(),s.getProcessInstanceId());
  // La autorización de Camunda para correlacionar es del sistema; el portal comprueba
  // primero el propietario y registra el actor real en la instancia.
  auth("admin");
  runtime.setVariable(s.getProcessInstanceId(),"ultimoEventoPortalUsuario",user);
  if(s.getEventName().equals("LoteLiberado"))db.update("UPDATE ac_lote SET liberado=TRUE WHERE id=?",runtime.getVariable(s.getProcessInstanceId(),"loteId"));
  runtime.createMessageCorrelation(s.getEventName()).processInstanceId(s.getProcessInstanceId()).correlate();
 }finally{identity.clearAuthentication();}}
}
