package com.aguacumbre.service;
import java.math.BigDecimal;
import org.camunda.bpm.engine.delegate.DelegateExecution;
public final class Variables {
 private Variables() {}
 public static String texto(DelegateExecution e, String key) {
  Object v=e.getVariable(key);
  if (!(v instanceof String s) || s.isBlank() || s.length()>200) throw new IllegalArgumentException("Campo requerido o inválido: "+key);
  return s.trim();
 }
 public static long positivo(DelegateExecution e, String key) {
  Object v=e.getVariable(key);
  try { long n=new BigDecimal(String.valueOf(v)).longValueExact(); if(n<=0) throw new ArithmeticException(); return n; }
  catch (RuntimeException x) { throw new IllegalArgumentException(key+" debe ser un entero positivo (COP o unidades)"); }
 }
 public static boolean bool(DelegateExecution e,String key) {
  Object v=e.getVariable(key);
  if (!(v instanceof Boolean)) throw new IllegalArgumentException("Se requiere booleano: "+key);
  return (Boolean)v;
 }
}
