package com.aguacumbre.web;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.task.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class PortalService {
 private final RuntimeService runtime;private final TaskService tasks;private final IdentityService identity;private final PortalCatalog catalog;private final JdbcTemplate db;
 public PortalService(RuntimeService r,TaskService t,IdentityService i,PortalCatalog c,JdbcTemplate db){runtime=r;tasks=t;identity=i;catalog=c;this.db=db;}
 private void auth(String user){identity.setAuthentication(user,identity.createGroupQuery().groupMember(user).list().stream().map(g->g.getId()).toList());}
 /** Los tres procesos inician con un evento de mensaje: el portal envía ese mensaje con la llave de negocio. */
 @Transactional public String iniciar(String key,Map<String,String> input,String user){auth(user);try{
  var p=catalog.proceso(key);var vars=catalog.convertir(p.formulario(),input);
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
 @Transactional public void completar(String id,Map<String,String> input,String user){auth(user);try{
  Task task=tasks.createTaskQuery().taskId(id).singleResult();if(task==null)throw new IllegalArgumentException("Tarea finalizada o inexistente");
  if(task.getAssignee()!=null&&!task.getAssignee().equals(user))throw new IllegalArgumentException("La tarea tiene otro responsable");
  var vars=catalog.convertir(catalog.formularioTarea(task),input);
  if(task.getAssignee()==null)tasks.claim(id,user);tasks.complete(id,vars);
 }finally{identity.clearAuthentication();}}
 /** Envía a una instancia concreta el mensaje que está esperando (lote liberado, devolución o pago). */
 @Transactional public void evento(String subscription,String user){auth(user);try{
  var s=runtime.createEventSubscriptionQuery().eventSubscriptionId(subscription).eventType("message").singleResult();
  if(s==null||s.getProcessInstanceId()==null||!catalog.eventos.containsKey(s.getEventName()))throw new IllegalArgumentException("La espera ya no está activa; actualice el portal");
  if(s.getEventName().equals("LoteLiberado"))db.update("UPDATE ac_lote SET liberado=TRUE WHERE id=?",runtime.getVariable(s.getProcessInstanceId(),"loteId"));
  runtime.createMessageCorrelation(s.getEventName()).processInstanceId(s.getProcessInstanceId()).correlate();
 }finally{identity.clearAuthentication();}}
}
