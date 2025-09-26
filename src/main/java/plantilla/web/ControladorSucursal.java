package plantilla.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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
        model.addAttribute("pantalla", "Menú sucursales");
        return "sucursales/lista";
    }

    @PostMapping("/guardar")
    public String guardarSucursal(@ModelAttribute("nuevaSucursal") Sucursal sucursal) {
        sucursalService.guardar(sucursal);
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
        model.addAttribute("nuevaSucursal", sucursal);
        model.addAttribute("sucursales", sucursalService.listarTodas());
        return "sucursales/lista"; // reutilizamos la misma vista
    }
}
