package com.aguacumbre.web;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PortalAccess {
 private final IdentityService identity;
 private final RuntimeService runtime;
 private final TaskService tasks;
 public PortalAccess(IdentityService identity,RuntimeService runtime,TaskService tasks){this.identity=identity;this.runtime=runtime;this.tasks=tasks;}
 public Set<String> groups(String user){return identity.createGroupQuery().groupMember(user).list().stream().map(g->g.getId()).collect(Collectors.toSet());}
 public boolean admin(String user){return "admin".equals(user);}
 public boolean internal(String user){return admin(user)||groups(user).stream().anyMatch(g->!"clientes".equals(g));}
 public void requireInternal(String user){if(!internal(user))throw new AccessDeniedException("Acceso reservado al personal interno");}
 public boolean canStart(String user,String key){return internal(user)||Set.of("distribuir_comercializar","equipos_campo").contains(key);}
 public void requireStart(String user,String key){if(!canStart(user,key))throw new AccessDeniedException("Proceso no permitido para esta cuenta");}
 public boolean canWork(String user,Task task){
  if(task==null)return false;
  if(admin(user))return true;
  if(task.getAssignee()!=null)return user.equals(task.getAssignee());
  Set<String> groups=groups(user);
  if(groups.contains("clientes"))return false;
  return tasks.getIdentityLinksForTask(task.getId()).stream().anyMatch(l->"candidate".equals(l.getType())&&groups.contains(l.getGroupId()));
 }
 public void requireWork(String user,Task task){if(!canWork(user,task))throw new AccessDeniedException("Esta tarea corresponde a otro rol");}
 public boolean ownsInstance(String user,String processInstanceId){
  if(internal(user))return true;
  Object owner=runtime.getVariable(processInstanceId,"solicitantePortal");
  return user.equals(owner);
 }
 public void requireInstance(String user,String processInstanceId){if(!ownsInstance(user,processInstanceId))throw new AccessDeniedException("Instancia no disponible para esta cuenta");}
 public boolean canSendEvent(String user,String eventName,String processInstanceId){
  if(admin(user))return true;
  Set<String> groups=groups(user);
  if(groups.stream().anyMatch(g->!"clientes".equals(g)))return switch(eventName){
   case "LoteLiberado" -> groups.contains("bodega")||groups.contains("calidad");
   case "PagoRecibido" -> groups.contains("tesoreria");
   case "DevolucionEquipo" -> groups.contains("bodega")||groups.contains("administracion");
   default -> false;
  };
  return Set.of("PagoRecibido","DevolucionEquipo").contains(eventName)&&ownsInstance(user,processInstanceId);
 }
 public void requireEvent(String user,String eventName,String processInstanceId){if(!canSendEvent(user,eventName,processInstanceId))throw new AccessDeniedException("Evento no permitido para esta cuenta");}
}
