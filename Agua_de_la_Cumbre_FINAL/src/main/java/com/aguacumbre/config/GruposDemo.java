package com.aguacumbre.config;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class GruposDemo {
 @Bean ApplicationRunner grupos(IdentityService identity,AuthorizationService authorization) {
  return args -> {
   for(String id:new String[]{"administracion","tesoreria","bodega","calidad","vendedores","tecnicos","clientes"}) {
    if(identity.createGroupQuery().groupId(id).count()==0) {
     var g=identity.newGroup(id);g.setName(id);g.setType("WORKFLOW");identity.saveGroup(g);
    }
    if(!id.equals("clientes")&&identity.createUserQuery().userId("admin").count()>0 && identity.createGroupQuery().groupId(id).groupMember("admin").count()==0)
     identity.createMembership("admin",id);
   }
   String pass=System.getenv().getOrDefault("DEMO_USER_PASSWORD","demo1234");
   for(String id:new String[]{"vendedores","bodega","administracion","tesoreria","tecnicos"}){
    if(identity.createUserQuery().userId(id).count()==0){var u=identity.newUser(id);u.setFirstName("Demo "+id);u.setPassword(pass);identity.saveUser(u);}
    if(identity.createGroupQuery().groupId(id).groupMember(id).count()==0)identity.createMembership(id,id);
   }
   if(authorization.createAuthorizationQuery().groupIdIn("clientes").resourceType(Resources.PROCESS_INSTANCE).resourceId("*").count()==0){
    var grant=authorization.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    grant.setGroupId("clientes");grant.setResource(Resources.PROCESS_INSTANCE);grant.setResourceId("*");grant.addPermission(Permissions.CREATE);
    authorization.saveAuthorization(grant);
   }
   for(String key:new String[]{"distribuir_comercializar","equipos_campo"}){
    if(authorization.createAuthorizationQuery().groupIdIn("clientes").resourceType(Resources.PROCESS_DEFINITION).resourceId(key).count()==0){
     var grant=authorization.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
     grant.setGroupId("clientes");grant.setResource(Resources.PROCESS_DEFINITION);grant.setResourceId(key);grant.addPermission(Permissions.CREATE_INSTANCE);
     authorization.saveAuthorization(grant);
    }
   }
  };
 }
}
