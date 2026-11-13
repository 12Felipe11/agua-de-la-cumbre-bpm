package com.aguacumbre.config;
import java.time.LocalDate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration
public class DemoData {
 @Bean ApplicationRunner datosDemostracion(JdbcTemplate db) {
  return args -> {
   for (String id : new String[]{"EQ-001","EQ-002","EQ-003"}) {
    if(db.queryForObject("SELECT COUNT(*) FROM ac_equipo WHERE id=?",Integer.class,id)==0)
     db.update("INSERT INTO ac_equipo(id,tipo,estado) VALUES(?,?,'DISPONIBLE')",id,id.equals("EQ-001")?"DISPENSADOR":"BOTELLON");
   }
   // Vida útil del botellón: 1 mes (Programa de Producto Terminado).
   lote(db,"LOTE-DEMO",true,LocalDate.now(),LocalDate.now().plusMonths(1),100);
   lote(db,"LOTE-PENDIENTE",false,LocalDate.now(),LocalDate.now().plusMonths(1),50);
   lote(db,"LOTE-VENCIDO",true,LocalDate.now().minusMonths(1).minusDays(1),LocalDate.now().minusDays(1),30);
  };
 }
 private static void lote(JdbcTemplate db,String id,boolean liberado,LocalDate fabricacion,LocalDate vence,long unidades) {
  if(db.queryForObject("SELECT COUNT(*) FROM ac_lote WHERE id=?",Integer.class,id)==0)
   db.update("INSERT INTO ac_lote(id,presentacion,fabricacion,vencimiento,disponible,liberado) VALUES(?,'BOTELLON_18_9_L',?,?,?,?)",id,fabricacion,vence,unidades,liberado);
 }
}
