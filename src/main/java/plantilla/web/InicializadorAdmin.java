/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package plantilla.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import plantilla.datos.UsuarioDao;
import plantilla.dominio.Rol;
import plantilla.dominio.Usuario;
import plantilla.util.EncriptarPassword;
@Component
public class InicializadorAdmin implements CommandLineRunner {

    @Autowired
    private UsuarioDao usuarioDao;

    @Override
    public void run(String... args) {
        if (usuarioDao.count() == 0) {

            Usuario admin = new Usuario();
            admin.setUsername("admin");
            admin.setInhabilitado(false);
            admin.setPassword(EncriptarPassword.encriptarPassword("admin"));

            // Asignamos directamente el rol al usuario
            Rol rolAdmin = new Rol();
            rolAdmin.setNombre("ROLE_ADMIN");
            admin.setRol(rolAdmin);

            usuarioDao.save(admin);
        }
    }
}

