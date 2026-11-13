package com.aguacumbre;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.camunda.bpm.engine.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:portal;DB_CLOSE_DELAY=-1","camunda.bpm.job-execution.enabled=false"})
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class PortalTest {
 @Autowired MockMvc mvc;@Autowired RuntimeService runtime;@Autowired TaskService tasks;
 @Test void logoDisponibleAntesDeIniciarSesion()throws Exception{
  mvc.perform(get("/brand/agua-de-la-cumbre.png")).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("/brand/agua-de-la-cumbre.png")));
 }
 @Test void muestraRegistros()throws Exception{mvc.perform(get("/portal/registros").with(httpBasic("admin","admin"))).andExpect(status().isOk()).andExpect(content().string(containsString("LOTE-DEMO")));}
 @Test void exigeLogin()throws Exception{mvc.perform(get("/portal").accept(org.springframework.http.MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());}
 @Test void portalConLogoYProcesosSinEsperasDeInicio()throws Exception{
  mvc.perform(get("/portal").with(httpBasic("admin","admin"))).andExpect(status().isOk())
   .andExpect(content().string(containsString("/brand/agua-de-la-cumbre.png")))
   .andExpect(content().string(containsString("Distribuir y comercializar (PR-CO-03)")))
   .andExpect(content().string(not(containsString("Registrar pago recibido"))));
  mvc.perform(get("/portal/iniciar/distribuir_comercializar").with(httpBasic("admin","admin"))).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"pedidoId\"")));
 }
 @Test void impidePostSinCsrf()throws Exception{mvc.perform(post("/portal/iniciar/equipos_campo").with(httpBasic("admin","admin"))).andExpect(status().isForbidden());}
 @Test void iniciaPorMensajeYCompletaTareaDesdeThymeleaf()throws Exception{
  mvc.perform(post("/portal/iniciar/distribuir_comercializar").with(httpBasic("admin","admin")).with(csrf()).param("pedidoId","WEB-1").param("clienteId","CLI-WEB")).andExpect(status().is3xxRedirection());
  var t=tasks.createTaskQuery().processInstanceBusinessKey("WEB-1").taskDefinitionKey("registrar").singleResult();
  mvc.perform(get("/portal/tareas/"+t.getId()).with(httpBasic("admin","admin"))).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"loteId\"")));
  mvc.perform(post("/portal/tareas/"+t.getId()).with(httpBasic("admin","admin")).with(csrf()).param("vendedorId","VEN-1").param("zona","Centro").param("canal","DIRECTO")
   .param("loteId","LOTE-DEMO").param("cantidad","2").param("precioUnitario","10000").param("saldo","999999")).andExpect(status().is3xxRedirection());
  org.assertj.core.api.Assertions.assertThat(tasks.createTaskQuery().processInstanceBusinessKey("WEB-1").taskDefinitionKey("organizar").count()).isEqualTo(1);
  org.assertj.core.api.Assertions.assertThat(runtime.getVariable(t.getProcessInstanceId(),"saldo")).isNull();
 }
 @Test void validaCamposEnServidor()throws Exception{
  mvc.perform(post("/portal/iniciar/distribuir_comercializar").with(httpBasic("admin","admin")).with(csrf()).param("pedidoId","WEB-INVALID")).andExpect(status().isOk()).andExpect(content().string(containsString("Campo requerido")));
 }
}
