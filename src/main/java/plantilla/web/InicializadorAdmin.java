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
import plantilla.datos.RolDao;
import plantilla.datos.UsuarioDao;
import plantilla.dominio.Rol;
import plantilla.dominio.Usuario;
import plantilla.util.EncriptarPassword;
@Component
public class InicializadorAdmin implements CommandLineRunner {

    @Autowired
    private UsuarioDao usuarioDao;

    @Autowired
    private RolDao rolDao;

    @Override
    public void run(String... args) {

        // 1. Inicializar roles
        Rol rolAdmin = crearRolSiNoExiste("ROLE_ADMIN");
        Rol rolOp    = crearRolSiNoExiste("ROLE_OP");

        // 2. Crear admin solo si no hay usuarios
        if (usuarioDao.count() == 0) {

            Usuario admin = new Usuario();
            admin.setUsername("admin");
            admin.setPassword(EncriptarPassword.encriptarPassword("admin"));
            admin.setInhabilitado(false);
            admin.setRol(rolAdmin);

            usuarioDao.save(admin);
        }
    }

    private Rol crearRolSiNoExiste(String nombre) {
        return rolDao.findByNombre(nombre)
                .orElseGet(() -> {
                    Rol r = new Rol();
                    r.setNombre(nombre);
                    return rolDao.save(r);
                });
    }
}
