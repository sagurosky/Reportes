package plantilla.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import plantilla.dominio.Sucursal;
import plantilla.servicio.SucursalService;

@Controller
@RequestMapping("/sucursales")
@RequiredArgsConstructor
public class ControladorSucursal {

    private final SucursalService sucursalService;

    @GetMapping("/")
    public String listarSucursales(Model model) {
        model.addAttribute("sucursales", sucursalService.listarTodas());
        model.addAttribute("nuevaSucursal", new Sucursal());
        model.addAttribute("esEdicion", false);
        model.addAttribute("pantalla", "Menú sucursales");
        return "sucursales/lista";
    }

    @PostMapping("/guardar")
    public String guardarSucursal(
            @ModelAttribute("nuevaSucursal") Sucursal sucursal,
            Model model,
            RedirectAttributes redirectAttributes) {

        boolean esEdicion = (sucursal.getId() != null);

        // 🔍 Validaciones de nombre duplicado
        Sucursal existente = sucursalService.buscarPorNombre(sucursal.getNombre());

        if (esEdicion) {
            if (existente != null && !existente.getId().equals(sucursal.getId())) {
                model.addAttribute("error", "Ya existe otra sucursal con ese nombre.");
                model.addAttribute("esEdicion", true);
                model.addAttribute("nuevaSucursal", sucursal);
                model.addAttribute("sucursales", sucursalService.listarTodas());
                return "sucursales/lista";
            }
        } else {
            if (existente != null) {
                model.addAttribute("error", "Ya existe una sucursal con ese nombre.");
                model.addAttribute("esEdicion", false);
                model.addAttribute("nuevaSucursal", sucursal);
                model.addAttribute("sucursales", sucursalService.listarTodas());
                return "sucursales/lista";
            }
        }
//DMS prueba gitignore 2
        // 💾 Guardar o actualizar
        sucursalService.guardar(sucursal);

        redirectAttributes.addFlashAttribute("success",
                esEdicion ? "Sucursal actualizada correctamente." : "Sucursal creada correctamente.");

        return "redirect:/sucursales/";
    }


    @PostMapping("/inhabilitar/{id}")
    public String inhabilitarSucursal(@PathVariable Long id) {
        sucursalService.inhabilitar(id);
        return "redirect:/sucursales/";
    }

    @PostMapping("/activar/{id}")
    public String activarSucursal(@PathVariable Long id) {
        sucursalService.activar(id);
        return "redirect:/sucursales/";
    }

    @GetMapping("/editar/{id}")
    public String editarSucursal(@PathVariable Long id, Model model) {
        Sucursal sucursal = sucursalService.buscarPorId(id);
        model.addAttribute("esEdicion", true);
        model.addAttribute("nuevaSucursal", sucursal);
        model.addAttribute("sucursales", sucursalService.listarTodas());
        return "sucursales/lista"; // reutilizamos la misma vista
    }
}
