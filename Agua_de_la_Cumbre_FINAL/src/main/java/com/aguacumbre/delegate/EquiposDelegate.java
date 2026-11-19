package com.aguacumbre.delegate;
import static com.aguacumbre.service.Variables.*;
import com.aguacumbre.service.RegistroService;
import org.camunda.bpm.engine.delegate.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** PR-SO-04: disponibilidad, asignación, mantenimiento y reintegro de equipos de campo. */
@Component("equiposDelegate")
public class EquiposDelegate implements JavaDelegate {
 private final JdbcTemplate db; private final RegistroService registro; private final String plazoAsignacion;
 public EquiposDelegate(JdbcTemplate db,RegistroService registro,@Value("${agua.plazos.asignacion:PT2M}") String plazoAsignacion) {
  this.db=db;this.registro=registro;this.plazoAsignacion=plazoAsignacion;
 }
 @Override public void execute(DelegateExecution e) {
  String accion=texto(e,"accion"), id=texto(e,"equipoId"), responsable=texto(e,"responsableId");
  switch(accion) {
   case "consultar" -> {
    var rows=db.queryForList("SELECT estado FROM ac_equipo WHERE id=?",id);
    if(rows.isEmpty()) throw new IllegalArgumentException("Equipo inexistente: "+id);
    boolean disponible="DISPONIBLE".equals(rows.get(0).get("ESTADO"));
    e.setVariable("equipoDisponible",disponible);
    if(e.getVariable("plazoAsignacion")==null) e.setVariable("plazoAsignacion",plazoAsignacion);
    if(!disponible) registro.registrar(e,"EQUIPO_NO_DISPONIBLE",id,"Solicitado por: "+responsable+"; estado: "+rows.get(0).get("ESTADO"),0);
   }
   case "asignar" -> {
    String acta=texto(e,"actaEntrega");
    if(db.update("UPDATE ac_equipo SET estado='ASIGNADO',responsable=?,version=version+1 WHERE id=? AND estado='DISPONIBLE'",responsable,id)!=1)
     throw new IllegalStateException("El equipo "+id+" ya no está disponible; otra solicitud lo asignó");
    registro.registrar(e,"EQUIPO_ASIGNADO",id,"Responsable: "+responsable+"; acta de entrega: "+acta,0);
   }
   case "mantenimiento" -> {
    db.update("UPDATE ac_equipo SET estado='MANTENIMIENTO',version=version+1 WHERE id=?",id);
    registro.registrar(e,"EQUIPO_MANTENIMIENTO",id,"Devuelto por: "+responsable,0);
   }
   case "reintegrar" -> {
    String acta=texto(e,"actaDevolucion");
    if(db.update("UPDATE ac_equipo SET estado='DISPONIBLE',responsable=NULL,version=version+1 WHERE id=?",id)!=1)
     throw new IllegalStateException("Equipo inexistente: "+id);
    registro.registrar(e,"EQUIPO_DEVUELTO",id,"Responsable anterior: "+responsable+"; acta de devolución: "+acta,0);
   }
   default -> throw new IllegalArgumentException("Acción de equipos no implementada: "+accion);
  }
 }
}
