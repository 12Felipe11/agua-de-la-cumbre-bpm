package com.aguacumbre.delegate;
import java.math.BigDecimal;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
/** Service Task configurada como Java Class (como en la guía): valida valor y comprobante sin modificar la cartera. */
public class ValidarPagoDelegate implements JavaDelegate {
 @Override public void execute(DelegateExecution e) {
  long pago;
  try { pago=new BigDecimal(String.valueOf(e.getVariable("pago"))).longValueExact(); } catch(RuntimeException x) { pago=0; }
  long saldo=((Number)e.getVariable("saldo")).longValue();
  boolean comprobante=e.getVariable("comprobantePago") instanceof String s && !s.isBlank() && s.length()<=200;
  e.setVariable("pagoValido",pago>0 && pago<=saldo && comprobante);
 }
}
