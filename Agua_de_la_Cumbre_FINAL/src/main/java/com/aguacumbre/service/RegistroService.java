package com.aguacumbre.service;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class RegistroService {
 private final JdbcTemplate db;
 public RegistroService(JdbcTemplate db) { this.db=db; }
 public void registrar(DelegateExecution e,String tipo,String ref,String detalle,long valor) {
  db.update("INSERT INTO ac_movimiento(instancia,tipo,referencia,detalle,valor) VALUES(?,?,?,?,?)",e.getProcessInstanceId(),tipo,ref,detalle,valor);
 }
}
