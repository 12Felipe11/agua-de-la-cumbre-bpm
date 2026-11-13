package com.aguacumbre.config;
import org.camunda.bpm.engine.IdentityService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class GruposDemo {
 @Bean ApplicationRunner grupos(IdentityService identity) {
  return args -> {
   for(String id:new String[]{"administracion","tesoreria","bodega","calidad","vendedores","tecnicos"}) {
    if(identity.createGroupQuery().groupId(id).count()==0) {
     var g=identity.newGroup(id);g.setName(id);g.setType("WORKFLOW");identity.saveGroup(g);
    }
    if(identity.createUserQuery().userId("admin").count()>0 && identity.createGroupQuery().groupId(id).groupMember("admin").count()==0)
     identity.createMembership("admin",id);
   }
  };
 }
}
