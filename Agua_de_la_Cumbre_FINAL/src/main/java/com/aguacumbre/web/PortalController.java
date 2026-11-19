package com.aguacumbre.web;
import org.camunda.bpm.engine.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;
import java.security.Principal;
import org.springframework.security.access.AccessDeniedException;
@Controller
public class PortalController {
 private final PortalCatalog catalog;private final PortalService service;private final RuntimeService runtime;private final TaskService tasks;private final ExternalTaskService external;private final PortalAccess access;private final HistoryService history;
 public PortalController(PortalCatalog c,PortalService s,RuntimeService r,TaskService t,ExternalTaskService e,PortalAccess access,HistoryService history){catalog=c;service=s;runtime=r;tasks=t;external=e;this.access=access;this.history=history;}
 @GetMapping("/")public String root(){return "redirect:/portal";}
 @GetMapping("/login")public String login(){return "login";}
 @GetMapping("/portal")public String home(Model m,Principal user){
  String name=user.getName();boolean internal=access.internal(name);
  m.addAttribute("interno",internal);
  m.addAttribute("procesos",catalog.procesos.stream().filter(p->access.canStart(name,p.id())).toList());
  m.addAttribute("tareas",internal?tasks.createTaskQuery().orderByTaskCreateTime().desc().list().stream().filter(t->access.canWork(name,t)).toList():List.of());
  // Solo esperas de instancias en curso: las suscripciones de los eventos de inicio no tienen instancia.
  m.addAttribute("mensajes",runtime.createEventSubscriptionQuery().eventType("message").list().stream()
   .filter(s->s.getProcessInstanceId()!=null&&catalog.eventos.containsKey(s.getEventName())&&access.canSendEvent(name,s.getEventName(),s.getProcessInstanceId())).toList());
  m.addAttribute("eventos",catalog.eventos);
  m.addAttribute("externas",internal?external.createExternalTaskQuery().list():List.of());
  m.addAttribute("instancias",runtime.createProcessInstanceQuery().rootProcessInstances().list().stream().filter(p->access.ownsInstance(name,p.getId())).toList());
  m.addAttribute("finalizadas",internal?List.of():history.createHistoricProcessInstanceQuery().variableValueEquals("solicitantePortal",name).finished().orderByProcessInstanceEndTime().desc().listPage(0,20));
  return "portal";}
 private String mostrar(String title,String schema,String action,Map<String,?> valores,Model m){m.addAttribute("titulo",title);m.addAttribute("campos",catalog.campos(schema));m.addAttribute("action",action);m.addAttribute("valores",valores);return "formulario";}
 @GetMapping("/portal/iniciar/{key}")public String inicio(@PathVariable String key,Model m,Principal user){access.requireStart(user.getName(),key);var p=catalog.proceso(key);return mostrar(p.nombre(),p.formulario(),"/portal/iniciar/"+key,access.internal(user.getName())?catalog.porDefecto(p.formulario()):Map.of(),m);}
 @PostMapping("/portal/iniciar/{key}")public String iniciar(@PathVariable String key,@RequestParam Map<String,String> input,Model m,Principal user,RedirectAttributes flash){try{
  access.requireStart(user.getName(),key);
  String id=service.iniciar(key,input,user.getName());flash.addFlashAttribute("ok","Instancia iniciada: "+id);return "redirect:/portal";
 }catch(AccessDeniedException e){throw e;}catch(RuntimeException e){m.addAttribute("error",mensaje(e));var p=catalog.proceso(key);return mostrar(p.nombre(),p.formulario(),"/portal/iniciar/"+key,input,m);}}
 @GetMapping("/portal/tareas/{id}")public String tarea(@PathVariable String id,Model m,Principal user){var t=tasks.createTaskQuery().taskId(id).singleResult();if(t==null)return "redirect:/portal";access.requireWork(user.getName(),t);String f=catalog.formularioTarea(t);Map<String,Object> v=new HashMap<>(catalog.porDefecto(f));v.putAll(tasks.getVariables(id));return mostrar(t.getName(),f,"/portal/tareas/"+id,v,m);}
 @PostMapping("/portal/tareas/{id}")public String completar(@PathVariable String id,@RequestParam Map<String,String> input,Model m,Principal user,RedirectAttributes flash){try{service.completar(id,input,user.getName());flash.addFlashAttribute("ok","Tarea completada. Actualice para ver tareas o eventos nuevos.");return "redirect:/portal";}catch(AccessDeniedException e){throw e;}catch(RuntimeException e){m.addAttribute("error",mensaje(e));var t=tasks.createTaskQuery().taskId(id).singleResult();if(t==null)return "redirect:/portal";Map<String,Object> v=new HashMap<>(tasks.getVariables(id));v.putAll(input);return mostrar(t.getName(),catalog.formularioTarea(t),"/portal/tareas/"+id,v,m);}}
 @PostMapping("/portal/eventos/{id}")public String evento(@PathVariable String id,Principal user,RedirectAttributes flash){try{service.evento(id,user.getName());flash.addFlashAttribute("ok","Mensaje enviado al motor.");}catch(AccessDeniedException e){throw e;}catch(RuntimeException e){flash.addFlashAttribute("error",mensaje(e));}return "redirect:/portal";}
 @GetMapping("/portal/instancias/{id}")public String variables(@PathVariable String id,Model m,Principal user){access.requireInternal(user.getName());var p=runtime.createProcessInstanceQuery().processInstanceId(id).singleResult();if(p==null)return "redirect:/portal";m.addAttribute("id",id);m.addAttribute("variables",runtime.getVariables(id));return "variables";}
 private String mensaje(Throwable e){
  for(Throwable x=e;x!=null;x=x.getCause()) if(x instanceof IllegalArgumentException||x instanceof IllegalStateException) return x.getMessage();
  return e.getMessage()==null?"No se pudo completar la operación":e.getMessage();
 }
}
