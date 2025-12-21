package plantilla.web;


import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import plantilla.dominio.EventoCarga;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.EventoCargaRepository;
import plantilla.repositorios.SucursalRepository;
import plantilla.servicio.StockService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/eventosCarga")
public class EventoCargaController {

    private final EventoCargaRepository eventoCargaRepository;

    public EventoCargaController(EventoCargaRepository eventoCargaRepository) {
        this.eventoCargaRepository = eventoCargaRepository;
    }

    @GetMapping("/")
    public String listarEventos(Model model) {
        List<EventoCarga> eventos = eventoCargaRepository.findAll();
        eventos.sort(Comparator.comparing(EventoCarga::getFecha).reversed());

        model.addAttribute("eventos", eventos);
        return "eventos/lista"; // 👈 plantilla Thymeleaf
    }

    @GetMapping("/{id}/estado")
    @ResponseBody
    public ResponseEntity<?> estadoEvento(@PathVariable Long id) {

        EventoCarga e = eventoCargaRepository.findById(id)
                .orElseThrow();

        return ResponseEntity.ok(Map.of(
                "estado", e.getEstado(),
                "procesados", e.getProcesados(),
                "total", e.getTotalRegistros(),
                "porcentaje", e.getPorcentaje(),
                "observaciones", e.getObservaciones()
        ));
    }
}



    

