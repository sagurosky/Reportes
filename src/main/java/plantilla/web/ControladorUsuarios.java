package plantilla.web;

import javax.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import plantilla.datos.RolDao;
import plantilla.datos.UsuarioDao;
import plantilla.dominio.Rol;
import plantilla.dominio.Usuario;
import plantilla.servicio.UsuarioService;
import plantilla.util.EncriptarPassword;

@Controller
@Slf4j
public class ControladorUsuarios {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioDao usuarioDao;

    @Autowired
    private RolDao rolDao;

    @GetMapping("/gestionUsuarios")
    public String gestionarUsuarios(Model model) {
//        var usuarios = usuarioService.listarUsuariosHabilitados();
        var usuarios = usuarioService.listarUsuariosTodos();

        model.addAttribute("pantalla", "Menú usuarios");
        model.addAttribute("usuarios", usuarios);
        return "gestionUsuarios";
    }

    @GetMapping("/crearUsuario")
    public String crearUsuario(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("rol", new Rol());
        return "crearUsuario";
    }


    @PostMapping("/gestionar")
    public String gestionar(
            @Valid @ModelAttribute("usuario") Usuario usuario,
            Errors errores,
            @RequestParam("rolRadio") String rolRadio,
            Model model) {

//        Usuario usuarioExistente = usuarioDao.findByUsername(usuario.getUsername());
        Usuario usuarioExistente = usuarioDao.traerUsuarioHabilitadoPorNombre(usuario.getUsername());

        // Solo bloqueamos si el usuario ya existe Y es una creación
        if (errores.hasErrors() || usuarioExistente != null) {
            if (usuarioExistente != null) {
                errores.rejectValue("username", "500", "Ya existe ese usuario");
            }
            return "crearUsuario";

        }

        Rol rol = new Rol();
        rol.setNombre(rolRadio);
        usuario.setInhabilitado(Boolean.FALSE);
        usuario.setRol(rol);
        usuario.setPassword(EncriptarPassword.encriptarPassword(usuario.getPassword()));

        usuarioService.guardar(usuario);

        return "redirect:/gestionUsuarios";
    }

    @GetMapping("/editarUsuario/{idUsuario}")
    public String editarUsuario(@PathVariable("idUsuario") Long idUsuario, Model model) {

        Usuario usuario = usuarioDao.findById(idUsuario).orElse(null);
        if (usuario != null) {
            usuario.setPassword(""); // Se envía vacío para que el usuario decida si lo cambia
            model.addAttribute("usuario", usuario);
            model.addAttribute("rolRadio", usuario.getRol().getNombre()); // Para marcar el radio button
        }
        return "crearUsuario"; // Usa la misma plantilla para crear y modificar
    }

    @PostMapping("/guardarUsuarioEditado")
    public String guardarUsuarioEditado(
            Model model,
            @RequestParam("rolRadio") String rolRadio,
            @ModelAttribute("usuario") Usuario usuarioReq,
            Errors errores) {

        Usuario usuarioOriginal = usuarioDao.findById(usuarioReq.getIdUsuario()).orElse(null);

        if (usuarioOriginal == null) {
            return "redirect:/gestionUsuarios";
        }
        if (usuarioOriginal.getIdUsuario() == 1 || usuarioOriginal.getUsername().equals("admin")) {
            log.info("pp");
            model.addAttribute("usuario", usuarioOriginal);
            model.addAttribute("rolRadio", usuarioOriginal.getRol().getNombre());
            model.addAttribute("mensaje", "no se puede editar el superusuario");
            return "crearUsuario";
        }

        usuarioOriginal.setUsername(usuarioReq.getUsername());
        usuarioOriginal.setNombreApellido(usuarioReq.getNombreApellido());
        usuarioOriginal.setTelefono(usuarioReq.getTelefono());
        usuarioOriginal.setObservacion(usuarioReq.getObservacion());

        // Si el campo password está vacío, mantenemos el actual
        if (usuarioReq.getPassword() != null && !usuarioReq.getPassword().trim().isEmpty()) {
            usuarioOriginal.setPassword(EncriptarPassword.encriptarPassword(usuarioReq.getPassword()));
        }

        usuarioOriginal.getRol().setNombre(rolRadio);
        usuarioDao.save(usuarioOriginal);

        return "redirect:/gestionUsuarios";
    }

    @GetMapping("/eliminarUsuario/{idUsuario}")
    public String eliminarUsuario(@PathVariable("idUsuario") Long idUsuario, Model model) {
        Usuario usuario = usuarioDao.findById(idUsuario).orElse(null);

        if (usuario.getIdUsuario() == 1 || usuario.getUsername().equals("admin")) {
            log.info("pp");
            model.addAttribute("mensaje", "no se puede inhabilitar el superusuario");
            return "redirect:/gestionUsuarios";
        }
        if (usuario != null) {
            usuario.setInhabilitado(Boolean.TRUE);
            usuarioDao.save(usuario);
        }

        return "redirect:/gestionUsuarios";
    }

    @GetMapping("/habilitarUsuario/{idUsuario}")
    public String habilitarUsuario(@PathVariable("idUsuario") Long idUsuario) {
        Usuario usuario = usuarioDao.findById(idUsuario).orElse(null);
        if (usuario != null) {
            usuario.setInhabilitado(Boolean.FALSE);
            usuarioDao.save(usuario);
        }
        return "redirect:/gestionUsuarios";
    }
}
