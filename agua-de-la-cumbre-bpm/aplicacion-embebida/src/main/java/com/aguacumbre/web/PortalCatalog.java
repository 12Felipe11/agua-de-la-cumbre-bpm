package com.aguacumbre.web;
import com.fasterxml.jackson.databind.*;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class PortalCatalog {
 public record Proceso(String id,String nombre,String formulario,String mensaje,String llave,String descripcion) {}
 public record Campo(String key,String label,String tipo,boolean requerido,boolean lectura,List<String> opciones) {}
 public record Evento(String nombre,String accion,String ayuda) {}
 public final List<Proceso> procesos=List.of(
  new Proceso("distribuir_comercializar","Distribuir y comercializar (PR-CO-03)","dist_solicitud","PedidoRecibido","pedidoId","Pedido del cliente o distribuidor, despacho de lote liberado y salida F-AC-PT-002."),
  new Proceso("equipos_campo","Asignar y recuperar equipos (PR-SO-04)","eq_solicitud","SolicitudEquipo","equipoId","Entrega con acta, devolución o vencimiento y mantenimiento."),
  new Proceso("facturar_recaudar","Facturar y recaudar (PR-SO-05)","fin_venta_manual","VentaEntregada","pedidoId","Inicia solo al terminar PR-CO-03; aquí puede registrarse una venta manual."));
 /** Mensajes que el portal puede enviar a una instancia en espera (actores externos simulados). */
 public final Map<String,Evento> eventos=Map.of(
  "LoteLiberado",new Evento("LoteLiberado","Liberar lote (simula PR-CO-02)","El pedido espera un lote liberado y vigente."),
  "DevolucionEquipo",new Evento("DevolucionEquipo","Registrar solicitud de devolución","El equipo está asignado al cliente o vendedor."),
  "PagoRecibido",new Evento("PagoRecibido","Registrar pago recibido","La factura está enviada y la cuenta espera el pago."));
 private final ObjectMapper mapper; private final RepositoryService repository;
 private final Map<String,String> formularios=new ConcurrentHashMap<>();
 public PortalCatalog(ObjectMapper mapper,RepositoryService repository){this.mapper=mapper;this.repository=repository;}
 public Proceso proceso(String id){return procesos.stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Proceso no permitido"));}
 /** El formulario de cada tarea se lee del camunda:formRef del modelo desplegado. */
 public String formularioTarea(Task t){
  return formularios.computeIfAbsent(t.getProcessDefinitionId()+"#"+t.getTaskDefinitionKey(),k->{
   ModelElementInstance el=repository.getBpmnModelInstance(t.getProcessDefinitionId()).getModelElementById(t.getTaskDefinitionKey());
   String f=el==null?null:el.getAttributeValueNs("http://camunda.org/schema/1.0/bpmn","formRef");
   if(f==null||f.isBlank())throw new IllegalArgumentException("Tarea sin formulario");
   return f;
  });
 }
 public List<Campo> campos(String id){
  try(var in=new ClassPathResource("static/form/"+id+".form").getInputStream()) {
   var root=mapper.readTree(in);List<Campo> result=new ArrayList<>();
   for(JsonNode c:root.path("components")) {
    if(!c.has("key"))continue;
    List<String> opciones=new ArrayList<>();for(var o:c.path("values"))opciones.add(o.path("value").asText());
    result.add(new Campo(c.path("key").asText(),c.path("label").asText(),c.path("type").asText(),c.path("validate").path("required").asBoolean(false),c.path("disabled").asBoolean(false),opciones));
   }
   return result;
  }catch(Exception e){throw new IllegalArgumentException("No se pudo leer formulario "+id,e);}
 }
 /** Valores por defecto (defaultValue) definidos en el .form, para mostrar el formulario ya diligenciado. */
 public Map<String,Object> porDefecto(String id){
  try(var in=new ClassPathResource("static/form/"+id+".form").getInputStream()) {
   Map<String,Object> result=new HashMap<>();
   for(JsonNode c:mapper.readTree(in).path("components")) {
    JsonNode d=c.get("defaultValue");
    if(!c.has("key")||d==null||d.isNull())continue;
    result.put(c.path("key").asText(),d.isBoolean()?(Object)d.booleanValue():d.isNumber()?(Object)d.asLong():d.asText());
   }
   return result;
  }catch(Exception e){throw new IllegalArgumentException("No se pudo leer formulario "+id,e);}
 }
 public Map<String,Object> convertir(String id,Map<String,String> input){
  Map<String,Object> result=new HashMap<>();
  for(Campo c:campos(id)) {
   if(c.lectura())continue;
   String s=input.getOrDefault(c.key(),"").trim();
   if(c.tipo().equals("checkbox")){result.put(c.key(),"true".equals(s));continue;}
   if(c.requerido()&&s.isEmpty())throw new IllegalArgumentException("Campo requerido: "+c.label());
   if(s.length()> (c.tipo().equals("textarea")?1000:200))throw new IllegalArgumentException("Texto demasiado largo: "+c.label());
   if(s.isEmpty())continue;
   if(c.tipo().equals("number")) {
    long n;try{n=Long.parseLong(s);}catch(Exception e){throw new IllegalArgumentException("Use un entero en "+c.label());}
    if(n<=0)throw new IllegalArgumentException("Valor positivo requerido: "+c.label());result.put(c.key(),n);
   } else {
    if(!c.opciones().isEmpty()&&!c.opciones().contains(s))throw new IllegalArgumentException("Opción inválida: "+c.label());
    if(Set.of("pedidoId","documentoId","equipoId").contains(c.key())&&s.length()>80)throw new IllegalArgumentException("Identificador máximo de 80 caracteres");
    result.put(c.key(),s);
   }
  }
  return result;
 }
}
