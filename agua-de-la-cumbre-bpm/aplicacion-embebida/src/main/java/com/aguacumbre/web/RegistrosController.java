package com.aguacumbre.web;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.jdbc.core.JdbcTemplate;
@Controller
public class RegistrosController {
 private final JdbcTemplate db;
 public RegistrosController(JdbcTemplate db){this.db=db;}
 @GetMapping("/portal/registros")public String registros(Model m){
  m.addAttribute("equipos",db.queryForList("SELECT id,tipo,estado,responsable FROM ac_equipo ORDER BY id"));
  m.addAttribute("lotes",db.queryForList("SELECT id,vencimiento,liberado,disponible FROM ac_lote ORDER BY id"));
  m.addAttribute("cuentas",db.queryForList("SELECT id,tipo,tercero,total,saldo,estado FROM ac_cuenta ORDER BY id"));
  m.addAttribute("pedidos",db.queryForList("SELECT id,cliente,canal,lote,cantidad,total,estado,comprobante FROM ac_pedido ORDER BY id"));
  m.addAttribute("movimientos",db.queryForList("SELECT tipo,referencia,detalle,valor,fecha FROM ac_movimiento ORDER BY id DESC LIMIT 40"));
  return "registros";
 }
}
