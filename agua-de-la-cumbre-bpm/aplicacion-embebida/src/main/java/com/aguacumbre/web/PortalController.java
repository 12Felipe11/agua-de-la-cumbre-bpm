package com.aguacumbre.web;
import org.camunda.bpm.engine.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;
import java.security.Principal;
@Controller
public class PortalController {
 private final PortalCatalog catalog;private final PortalService service;private final RuntimeService runtime;private final TaskService tasks;private final ExternalTaskService external;
 public PortalController(PortalCatalog c,PortalService s,RuntimeService r,TaskService t,ExternalTaskService e){catalog=c;service=s;runtime=r;tasks=t;external=e;}
 @GetMapping("/")public String root(){return "redirect:/portal";}
 @GetMapping("/login")public String login(){return "login";}
 @GetMapping("/portal")public String home(Model m){
  m.addAttribute("procesos",catalog.procesos);
  m.addAttribute("tareas",tasks.createTaskQuery().orderByTaskCreateTime().desc().list());
  // Solo esperas de instancias en curso: las suscripciones de los eventos de inicio no tienen instancia.
  m.addAttribute("mensajes",runtime.createEventSubscriptionQuery().eventType("message").list().stream()
   .filter(s->s.getProcessInstanceId()!=null&&catalog.eventos.containsKey(s.getEventName())).toList());
  m.addAttribute("eventos",catalog.eventos);
  m.addAttribute("externas",external.createExternalTaskQuery().list());
  m.addAttribute("instancias",runtime.createProcessInstanceQuery().rootProcessInstances().list());
  return "portal";}
 private String mostrar(String title,String schema,String action,Map<String,?> valores,Model m){m.addAttribute("titulo",title);m.addAttribute("campos",catalog.campos(schema));m.addAttribute("action",action);m.addAttribute("valores",valores);return "formulario";}
 @GetMapping("/portal/iniciar/{key}")public String inicio(@PathVariable String key,Model m){var p=catalog.proceso(key);return mostrar(p.nombre(),p.formulario(),"/portal/iniciar/"+key,catalog.porDefecto(p.formulario()),m);}
 @PostMapping("/portal/iniciar/{key}")public String iniciar(@PathVariable String key,@RequestParam Map<String,String> input,Model m,Principal user,RedirectAttributes flash){try{
  String id=service.iniciar(key,input,user.getName());flash.addFlashAttribute("ok","Instancia iniciada: "+id);return "redirect:/portal";
 }catch(RuntimeException e){m.addAttribute("error",mensaje(e));var p=catalog.proceso(key);return mostrar(p.nombre(),p.formulario(),"/portal/iniciar/"+key,input,m);}}
 @GetMapping("/portal/tareas/{id}")public String tarea(@PathVariable String id,Model m){var t=tasks.createTaskQuery().taskId(id).singleResult();if(t==null)return "redirect:/portal";String f=catalog.formularioTarea(t);Map<String,Object> v=new HashMap<>(catalog.porDefecto(f));v.putAll(tasks.getVariables(id));return mostrar(t.getName(),f,"/portal/tareas/"+id,v,m);}
 @PostMapping("/portal/tareas/{id}")public String completar(@PathVariable String id,@RequestParam Map<String,String> input,Model m,Principal user,RedirectAttributes flash){try{service.completar(id,input,user.getName());flash.addFlashAttribute("ok","Tarea completada. Actualice para ver tareas o eventos nuevos.");return "redirect:/portal";}catch(RuntimeException e){m.addAttribute("error",mensaje(e));var t=tasks.createTaskQuery().taskId(id).singleResult();if(t==null)return "redirect:/portal";Map<String,Object> v=new HashMap<>(tasks.getVariables(id));v.putAll(input);return mostrar(t.getName(),catalog.formularioTarea(t),"/portal/tareas/"+id,v,m);}}
 @PostMapping("/portal/eventos/{id}")public String evento(@PathVariable String id,Principal user,RedirectAttributes flash){try{service.evento(id,user.getName());flash.addFlashAttribute("ok","Mensaje enviado al motor.");}catch(RuntimeException e){flash.addFlashAttribute("error",mensaje(e));}return "redirect:/portal";}
 @GetMapping("/portal/instancias/{id}")public String variables(@PathVariable String id,Model m){var p=runtime.createProcessInstanceQuery().processInstanceId(id).singleResult();if(p==null)return "redirect:/portal";m.addAttribute("id",id);m.addAttribute("variables",runtime.getVariables(id));return "variables";}
 private String mensaje(Throwable e){
  for(Throwable x=e;x!=null;x=x.getCause()) if(x instanceof IllegalArgumentException||x instanceof IllegalStateException) return x.getMessage();
  return e.getMessage()==null?"No se pudo completar la operación":e.getMessage();
 }
}
