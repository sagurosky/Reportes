package plantilla.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import plantilla.dominio.EventoCarga;
import plantilla.servicio.EventoCargaService;
import plantilla.repositorios.EventoCargaRepository;

import java.util.*;

@Controller
@RequestMapping("/eventosCarga")
public class EventoCargaController {

    private final EventoCargaRepository eventoCargaRepository;
    @Autowired
    EventoCargaService eventoCargaService;

    public EventoCargaController(EventoCargaRepository eventoCargaRepository) {
        this.eventoCargaRepository = eventoCargaRepository;
    }

    @GetMapping("/")
    public String listarEventos(Model model) {
        List<EventoCarga> eventos = eventoCargaRepository.findAll();

        eventos.sort(Comparator.comparing(
                EventoCarga::getFecha,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        model.addAttribute("eventos", eventos);
        return "eventos/lista"; // 👈 plantilla Thymeleaf
    }

    @GetMapping("/{id}/estado")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<?> estadoEvento(@PathVariable Long id) {

        EventoCarga e = eventoCargaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento no encontrado"));

        Map<String, Object> response = new HashMap<>();
        response.put("estado", e.getEstado());
        response.put("procesados", e.getProcesados());
        response.put("total", e.getTotalRegistros());
        response.put("porcentaje", e.getPorcentaje());
        response.put("idStockInicial", e.getIdStockInicial());
        response.put("idStockFinal", e.getIdStockFinal());
        response.put("id", e.getId());

        return ResponseEntity.ok(response);
    }
}
