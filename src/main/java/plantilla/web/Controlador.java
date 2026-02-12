package plantilla.web;

import java.io.File;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

import org.apache.poi.hssf.OldExcelFormatException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import plantilla.dominio.EventoCarga;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.EventoCargaRepository;

import plantilla.servicio.EventoCargaService;
import plantilla.servicio.ReporteStockService;
import plantilla.servicio.S3Service;
import plantilla.servicio.StockAsyncService;
import plantilla.servicio.StockService;
import plantilla.repositorios.SucursalRepository;

@Controller
@Slf4j
public class Controlador {

    @Autowired
    private StockService stockService;

    @Autowired
    private EventoCargaRepository eventoCargaRepository;
    @Autowired
    private EventoCargaService eventoCargaService;

    @Autowired
    private StockAsyncService stockAsyncService;

    @Autowired
    S3Service s3Service;

    @Autowired
    private SucursalRepository sucursalRepository;

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

        // DMS conversiones por error en thymeleaf
        ZonedDateTime ahoraZona = ZonedDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        Date ahora = Date.from(ahoraZona.toInstant());
        model.addAttribute("ahora", ahora);
        return "cargarArchivos";
    }

    @PostMapping("/subirArchivosExcel")
    @ResponseBody
    public ResponseEntity<?> subirArchivo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fechaStock") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaStock) {
        log.info("📥 Recibido archivo: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "mensaje", "⚠️ El archivo está vacío."));
        }

        boolean hayCargaEnProceso = eventoCargaRepository.existsByEstado("EN_PROCESO");

        if (hayCargaEnProceso) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "mensaje", "⚠️  Ya existe una carga de stock en proceso"));
        }

        String nombreArchivo = file.getOriginalFilename();

        try (Workbook workbook = getWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "⚠️ El archivo no contiene datos."));
            }

            // 🚨 archivo duplicado
            if (eventoCargaService.existeArchivo(nombreArchivo)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "📛 El archivo '" + nombreArchivo + "' ya fue cargado previamente."));
            }

            // 🚨 fecha futura
            if (fechaStock.isAfter(LocalDate.now())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "📅 La fecha del stock no puede ser futura."));
            }

            if (!nombreArchivo.toLowerCase().contains("inventory")) {
                return ResponseEntity.ok(Map.of(
                        "status", "warn",
                        "mensaje", "⚠️ Tipo de archivo no reconocido: " + nombreArchivo));
            }

            // 🔐 usuario
            String usuario = Optional.ofNullable(
                    SecurityContextHolder.getContext().getAuthentication()).map(auth -> auth.getName())
                    .orElse("sistema");

            // 🏬 resolver sucursal desde archivo
            Sucursal sucursal = stockService.resolverSucursalDesdeArchivo(
                    sheet,
                    nombreArchivo);

            int totalRegistros = sheet.getPhysicalNumberOfRows() - 1;

            // 📌 crear evento inicial
            EventoCarga evento = eventoCargaService.crearEventoInicial(
                    nombreArchivo,
                    sucursal,
                    fechaStock,
                    usuario,
                    totalRegistros);

            // Usamos la carpeta temporal del sistema
            String tempDir = System.getProperty("java.io.tmpdir");
            String archivoNombre = file.getOriginalFilename();
            File tempFile = new File(tempDir, archivoNombre + "_" + System.currentTimeMillis() + ".xlsx");
            file.transferTo(tempFile);

            // 3. NUEVO: Llamar al servicio async pasando el PATH del archivo, no el Sheet
            stockAsyncService.procesarDesdeLocal(
                    evento.getId(),
                    tempFile.getAbsolutePath(),
                    fechaStock);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "eventoId", evento.getId(),
                    "mensaje", "📦 Archivo recibido localmente: " + nombreArchivo + ". Procesando en segundo plano."));

        } catch (Exception e) {

            String mensaje;
            if (e instanceof OldExcelFormatException || e.getMessage().contains("BIFF5")) {
                mensaje = "📛 Formato Excel muy antiguo. Guardalo como .xlsx.";
            } else {
                mensaje = "❌ Error al procesar el archivo: " + e.getMessage();
            }

            log.error("❌ Error al procesar archivo {}", nombreArchivo, e);

            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "mensaje", mensaje));
        }
    }

    private Workbook getWorkbook(MultipartFile file) throws IOException {

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            throw new IllegalArgumentException("Nombre de archivo nulo.");
        }

        String lower = originalName.toLowerCase();

        try (var is = file.getInputStream()) {

            if (lower.endsWith(".xlsx")) {
                return new XSSFWorkbook(is);
            }

            if (lower.endsWith(".xls")) {
                try {
                    return new HSSFWorkbook(is); // BIFF8
                } catch (OldExcelFormatException e) {
                    throw new OldExcelFormatException(
                            "Formato Excel muy antiguo (BIFF5). Convertir a .xlsx.");
                }
            }

            throw new IllegalArgumentException("Archivo no soportado: " + originalName);
        }
    }

    @PostMapping("/subirArchivosExcelS3")
    @ResponseBody
    public ResponseEntity<?> subirArchivoS3(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fechaStock") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaStock,
            @RequestParam(value = "observaciones", required = false) String observaciones) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "mensaje", "⚠️ El archivo está vacío."));
        }

        boolean hayCargaEnProceso = eventoCargaRepository.existsByEstado("EN_PROCESO");

        if (hayCargaEnProceso) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "mensaje", "⚠️  Ya existe una carga de stock en proceso"));
        }

        String nombreArchivo = file.getOriginalFilename();

        try (Workbook workbook = getWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "⚠️ El archivo no contiene datos."));
            }

            // 🚨 archivo duplicado
            if (eventoCargaService.existeArchivo(nombreArchivo)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "📛 El archivo '" + nombreArchivo + "' ya fue cargado previamente."));
            }

            // 🚨 fecha futura
            if (fechaStock.isAfter(LocalDate.now())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "📅 La fecha del stock no puede ser futura."));
            }

            if (!nombreArchivo.toLowerCase().contains("inventory")) {
                return ResponseEntity.ok(Map.of(
                        "status", "warn",
                        "mensaje", "⚠️ Tipo de archivo no reconocido: " + nombreArchivo));
            }

            // 🔐 usuario
            String usuario = Optional.ofNullable(
                    SecurityContextHolder.getContext().getAuthentication()).map(auth -> auth.getName())
                    .orElse("sistema");

            // 🏬 resolver sucursal desde archivo
            Sucursal sucursal = stockService.resolverSucursalDesdeArchivo(
                    sheet,
                    nombreArchivo);

            int totalRegistros = sheet.getPhysicalNumberOfRows() - 1;

            String s3Key = "stock/" + UUID.randomUUID() + "-" + nombreArchivo;

            // 1️⃣ Subir a S3
            s3Service.subirArchivo(s3Key, file);

            // 2️⃣ Crear evento

            EventoCarga evento = eventoCargaService.crearEventoInicialS3(
                    nombreArchivo,
                    sucursal,
                    fechaStock,
                    usuario,
                    totalRegistros,
                    s3Key,
                    observaciones);

            // 3️⃣ Lanzar async
            stockAsyncService.procesarDesdeS3(
                    evento.getId(),
                    fechaStock);

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "eventoId", evento.getId(),
                    "mensaje",
                    "📦 Archivo recibido: " + file.getOriginalFilename() + ". Procesando en segundo plano."));

        } catch (Exception e) {

            String mensaje;
            if (e instanceof OldExcelFormatException || e.getMessage().contains("BIFF5")) {
                mensaje = "📛 Formato Excel muy antiguo. Guardalo como .xlsx.";
            } else {
                mensaje = "❌ Error al procesar el archivo: " + e.getMessage();
            }

            log.error("❌ Error al procesar archivo {}", nombreArchivo, e);

            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "mensaje", mensaje));
        } finally {

        }

    }

    // DMS cuando convertía los archivos con format antiguo, no debería necesitarlo
    // mas
    // private File convertirConLibreOffice(File archivoOriginal) throws
    // IOException, InterruptedException {
    // String tempDir = "/tmp";
    //
    // ProcessBuilder pb = new ProcessBuilder(
    // "libreoffice", "--headless", "--convert-to", "xlsx",
    // archivoOriginal.getAbsolutePath(), "--outdir", tempDir
    // );
    //
    // pb.redirectErrorStream(true);
    // Process process = pb.start();
    // int exitCode = process.waitFor();
    //
    // if (exitCode != 0) {
    // throw new IOException("LibreOffice falló al convertir el archivo. Código: " +
    // exitCode);
    // }
    //
    // File convertido = new File(tempDir, archivoOriginal.getName().replace(".xls",
    // ".xlsx"));
    // if (!convertido.exists()) {
    // throw new IOException("Archivo convertido no encontrado: " +
    // convertido.getAbsolutePath());
    // }
    //
    // return convertido;
    // }

    @Autowired
    private ReporteStockService reporteStockService;

    // Reportes - Stock Dashboard
    @GetMapping("/reportes/stock")
    public String reportesStock(
            @RequestParam(name = "color", required = false, defaultValue = "stock-3") String color,
            @RequestParam(name = "sucursalId", required = false) Long sucursalId,
            @RequestParam(name = "fechaInicio", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(name = "fechaFin", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            Model model) {

        // Default to last 30 days if no dates provided
        if (fechaInicio == null) {
            fechaInicio = LocalDate.now().minusDays(30);
        }
        if (fechaFin == null) {
            fechaFin = LocalDate.now();
        }

        log.info("📊 Dashboard de Stock - Sucursal: {}, Fechas: {} a {}", sucursalId, fechaInicio, fechaFin);

        model.addAttribute("pantalla", "Reportes de Stock");
        model.addAttribute("colorClase", color);
        model.addAttribute("fechaInicio", fechaInicio);
        model.addAttribute("fechaFin", fechaFin);

        // Sucursal list for filter dropdown
        model.addAttribute("sucursales", sucursalRepository.findByInhabilitadoFalse());
        model.addAttribute("sucursalSeleccionada", sucursalId);

        // Data will be loaded via AJAX - no heavy queries here
        return "reporteStock";
    }

    // REST API Endpoints for AJAX data loading

    @GetMapping("/api/reportes/stock/evolution")
    @ResponseBody
    public ResponseEntity<?> getStockEvolution(
            @RequestParam(name = "sucursalId", required = false) Long sucursalId,
            @RequestParam(name = "fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(name = "fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        try {
            return ResponseEntity
                    .ok(reporteStockService.getStockEvolutionByAmbiente(fechaInicio, fechaFin, sucursalId));
        } catch (Exception e) {
            log.error("Error fetching stock evolution", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error al cargar evolución de stock"));
        }
    }

    @GetMapping("/api/reportes/stock/treemap")
    @ResponseBody
    public ResponseEntity<?> getTreemapData(@RequestParam(name = "sucursalId", required = false) Long sucursalId) {
        try {
            return ResponseEntity.ok(reporteStockService.getTreemapData(sucursalId));
        } catch (Exception e) {
            log.error("Error fetching treemap data", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error al cargar mapa de inventario"));
        }
    }

    @GetMapping("/api/reportes/stock/stockouts")
    @ResponseBody
    public ResponseEntity<?> getStockouts(
            @RequestParam(name = "sucursalId", required = false) Long sucursalId,
            @RequestParam(name = "fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio) {
        try {
            return ResponseEntity.ok(reporteStockService.getStockoutsByDayOfWeek(sucursalId, fechaInicio));
        } catch (Exception e) {
            log.error("Error fetching stockouts", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error al cargar análisis de quiebres"));
        }
    }

    @GetMapping("/api/reportes/stock/consumo-vs-stock")
    @ResponseBody
    public ResponseEntity<?> getConsumoVsStock(
            @RequestParam(name = "sucursalId", required = false) Long sucursalId,
            @RequestParam(name = "fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(name = "fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        try {
            return ResponseEntity.ok(reporteStockService.getConsumoVsStock(fechaInicio, fechaFin, sucursalId));
        } catch (Exception e) {
            log.error("Error fetching consumo vs stock", e);
            return ResponseEntity.status(500).body(Map.of("error", "Error al cargar consumo vs stock"));
        }
    }
}
