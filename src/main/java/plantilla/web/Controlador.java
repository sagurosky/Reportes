package plantilla.web;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.EventoCargaRepository;
import plantilla.repositorios.SucursalRepository;
import plantilla.servicio.StockService;


@Controller
@Slf4j
public class Controlador {

    @Autowired
    private StockService stockService;

    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private EventoCargaRepository eventoCargaRepository;


    // Menú
    @GetMapping("/menuPrincipal")
    public String inicio(Model model) {
        model.addAttribute("message1", "");
        model.addAttribute("pantalla", "Menú principal");
        return "menuPrincipal";
    }

    // Vista carga de archivos
    @GetMapping("/cargarArchivos/{nombre}")
    public String cargaArchivos(@PathVariable("nombre") String nombre,
                                @RequestParam(name = "color", required = false, defaultValue = "color-stock") String color,
                                Model model) {
        model.addAttribute("message1", "");
        model.addAttribute("nombre", nombre);
        model.addAttribute("pantalla", "Carga de archivos de " + nombre);
        model.addAttribute("colorClase", color);
        // Solo sucursales activas
        List<Sucursal> sucursales = sucursalRepository.findByInhabilitadoFalse();
        model.addAttribute("sucursales", sucursales);

        //DMS conversiones por error en thymeleaf
        ZonedDateTime ahoraZona = ZonedDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        Date ahora = Date.from(ahoraZona.toInstant());
        model.addAttribute("ahora", ahora);
        return "cargarArchivos";
    }

    // Procesar Excel
    @PostMapping("/subirArchivosExcel")
    @ResponseBody
    public ResponseEntity<?> subirArchivo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sucursalId") Long sucursalId,
            @RequestParam("fechaStock") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaStock) {
        log.info("📥 Recibido archivo: {}", file.getOriginalFilename());

        try (Workbook workbook = getWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            String nombre = file.getOriginalFilename().toLowerCase();

            if (sucursalId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "Debe seleccionar una sucursal antes de cargar el archivo"
                ));
            }

            Optional<Sucursal> optSucursal = sucursalRepository.findById(sucursalId);
            if (optSucursal.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "Sucursal no encontrada"
                ));
            }

            // ✅ NUEVA VALIDACIÓN: continuidad de fecha
            Optional<LocalDate> ultimaFechaOpt = eventoCargaRepository.findMaxFechaArchivoBySucursalId(sucursalId);
            LocalDate fechaEsperada;

            if (ultimaFechaOpt.isPresent()) {
                fechaEsperada = ultimaFechaOpt.get().plusDays(1);
                if (!fechaStock.equals(fechaEsperada)) {
                    String mensaje = String.format(
                            "❌ La fecha del stock debe ser el día siguiente a la última carga (%s). Fecha recibida: %s",
                            fechaEsperada, fechaStock
                    );
                    return ResponseEntity.badRequest().body(Map.of(
                            "status", "error",
                            "mensaje", mensaje
                    ));
                }
            } else {
                // Primera carga: permitir cualquier fecha ≤ hoy
                if (fechaStock.isAfter(LocalDate.now())) {
                    // Esto ya está bloqueado por el frontend, pero por seguridad:
                    return ResponseEntity.badRequest().body(Map.of(
                            "status", "error",
                            "mensaje", "La fecha no puede ser futura."
                    ));
                }
                // ✅ OK: primera carga válida
            }


            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "⚠️ El archivo está vacío o no contiene datos."
                ));
            }

            // 🚨 Validar nombre de archivo duplicado
            String nombreArchivo = file.getOriginalFilename();
            boolean existe = eventoCargaRepository.existsByNombreArchivo(nombreArchivo);
            if (existe) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "📛 El archivo '" + nombreArchivo + "' ya fue cargado previamente."
                ));
            }
            Sucursal sucursal = optSucursal.get();
            if (nombre.contains("rotacion")) {
                stockService.procesarRotacion(sheet, nombreArchivo, sucursal, fechaStock);
                return ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "mensaje", "✅ Procesado como Rotación: " + nombre + " para sucursal: " + sucursal.getNombre()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "status", "warn",
                        "mensaje", "⚠️ Tipo desconocido: " + nombre
                ));
            }

        } catch (Exception e) {
            String mensaje;
            if (e instanceof OldExcelFormatException || e.getMessage().contains("BIFF5")) {
                log.info("archivo viejo");
                mensaje = "📛 Formato muy antiguo (Excel 5.0/7.0 - BIFF5). " +
                        "💡 Abrilo en Excel y usá 'Guardar como...' ➡️ .xlsx";
            } else {
                mensaje = "Error al procesar el archivo: " + e.getMessage();
            }

            log.error("❌ Error al procesar archivo", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "error",
                    "mensaje", mensaje
            ));
        }
    }


    private Workbook getWorkbook(MultipartFile file) throws Exception {
        String originalName = file.getOriginalFilename();
        if (originalName == null) throw new IllegalArgumentException("Nombre de archivo nulo.");

        String lower = originalName.toLowerCase();
        File tempXls = new File("/tmp", originalName);
        file.transferTo(tempXls);

        if (lower.endsWith(".xlsx")) {
            return new XSSFWorkbook(new FileInputStream(tempXls));
        } else if (lower.endsWith(".xls")) {
            try {
                return new HSSFWorkbook(new FileInputStream(tempXls)); // BIFF8
            } catch (OldExcelFormatException e) {
                log.warn("📛 Archivo BIFF5 detectado. Convirtiendo: {}", originalName);
                File convertido = convertirConLibreOffice(tempXls);
                return new XSSFWorkbook(new FileInputStream(convertido));
            }
        }

        throw new IllegalArgumentException("Archivo no soportado: " + originalName);
    }

    private File convertirConLibreOffice(File archivoOriginal) throws IOException, InterruptedException {
        String tempDir = "/tmp";

        ProcessBuilder pb = new ProcessBuilder(
                "libreoffice", "--headless", "--convert-to", "xlsx",
                archivoOriginal.getAbsolutePath(), "--outdir", tempDir
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("LibreOffice falló al convertir el archivo. Código: " + exitCode);
        }

        File convertido = new File(tempDir, archivoOriginal.getName().replace(".xls", ".xlsx"));
        if (!convertido.exists()) {
            throw new IOException("Archivo convertido no encontrado: " + convertido.getAbsolutePath());
        }

        return convertido;
    }
}


    

