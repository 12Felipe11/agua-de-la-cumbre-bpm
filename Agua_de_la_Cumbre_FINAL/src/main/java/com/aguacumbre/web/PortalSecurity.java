package com.aguacumbre.web;
import org.camunda.bpm.engine.IdentityService;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import java.util.ArrayList;
@Configuration
public class PortalSecurity {
 @Bean AuthenticationProvider camundaProvider(IdentityService identity){return new AuthenticationProvider(){
  public Authentication authenticate(Authentication a){String id=a.getName();String pass=String.valueOf(a.getCredentials());
   if(!identity.checkPassword(id,pass))throw new BadCredentialsException("Credenciales incorrectas");
   var authorities=new ArrayList<>(identity.createGroupQuery().groupMember(id).list().stream()
    .map(g->new SimpleGrantedAuthority("ROLE_"+g.getId().toUpperCase())).toList());
   if("admin".equals(id))authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
   return new UsernamePasswordAuthenticationToken(id,null,authorities);
  }
  public boolean supports(Class<?> c){return UsernamePasswordAuthenticationToken.class.isAssignableFrom(c);}
 };}
 @Bean SecurityFilterChain portal(HttpSecurity http,AuthenticationProvider provider)throws Exception {
  return http.authenticationProvider(provider)
   .authorizeHttpRequests(a->a.requestMatchers("/login","/registro","/app.css","/brand/**","/error","/static/**","/bpmn/**","/form/**","/dmn/**").permitAll()
    .requestMatchers("/engine-rest/**","/camunda/**").hasRole("ADMIN").anyRequest().authenticated())
   .formLogin(f->f.loginPage("/login").defaultSuccessUrl("/portal",true).permitAll())
   .httpBasic(b->{})
   .csrf(c->c.ignoringRequestMatchers("/engine-rest/**","/camunda/**"))
   .logout(l->l.logoutSuccessUrl("/login?logout")).build();
 }
}
