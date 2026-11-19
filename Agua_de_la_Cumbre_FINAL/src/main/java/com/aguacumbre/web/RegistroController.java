package com.aguacumbre.web;

import org.camunda.bpm.engine.IdentityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class RegistroController {
 private final IdentityService identity;
 public RegistroController(IdentityService identity){this.identity=identity;}
 @GetMapping("/registro")public String form(){return "registro";}
 @PostMapping("/registro")@Transactional public String registrar(@RequestParam String usuario,@RequestParam String nombre,@RequestParam String clave,Model model,RedirectAttributes flash){
  usuario=usuario.trim().toLowerCase();nombre=nombre.trim();
  if(!usuario.matches("[a-z0-9._-]{4,40}")||"admin".equals(usuario)||nombre.isBlank()||nombre.length()>100||clave.length()<8||clave.length()>100){
   model.addAttribute("error","Use un usuario de 4 a 40 caracteres, nombre y contraseña de al menos 8 caracteres.");return "registro";
  }
  if(identity.createUserQuery().userId(usuario).count()>0){model.addAttribute("error","Ese usuario ya existe.");return "registro";}
  var user=identity.newUser(usuario);user.setFirstName(nombre);user.setPassword(clave);identity.saveUser(user);
  identity.createMembership(usuario,"clientes");
  flash.addFlashAttribute("ok","Cuenta creada. Ingrese con sus credenciales.");return "redirect:/login";
 }
}
