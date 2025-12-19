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


        //DMS conversiones por error en thymeleaf
        ZonedDateTime ahoraZona = ZonedDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        Date ahora = Date.from(ahoraZona.toInstant());
        model.addAttribute("ahora", ahora);
        return "cargarArchivos";
    }

    //  Excel
    @PostMapping("/subirArchivosExcel")
    @ResponseBody
    public ResponseEntity<?> subirArchivo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fechaStock")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaStock
    ) {
        log.info("📥 Recibido archivo: {}", file.getOriginalFilename());

        try (Workbook workbook = getWorkbook(file)) {

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "⚠️ El archivo está vacío."
                ));
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "⚠️ El archivo no contiene datos."
                ));
            }

            String nombreArchivo = file.getOriginalFilename();

            // 🚨 Validar archivo duplicado
            if (eventoCargaRepository.existsByNombreArchivo(nombreArchivo)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "📛 El archivo '" + nombreArchivo + "' ya fue cargado previamente."
                ));
            }

            // 🚨 Validación de fecha futura (defensiva)
            if (fechaStock.isAfter(LocalDate.now())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "mensaje", "📅 La fecha del stock no puede ser futura."
                ));
            }

            String nombreLower = nombreArchivo.toLowerCase();

            if (nombreLower.contains("inventory")) {

                stockService.procesarStock(sheet, nombreArchivo, fechaStock);

                return ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "mensaje", "✅ Archivo procesado correctamente."
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "status", "warn",
                    "mensaje", "⚠️ Tipo de archivo no reconocido: " + nombreArchivo
            ));

        } catch (Exception e) {

            String mensaje;
            if (e instanceof OldExcelFormatException || e.getMessage().contains("BIFF5")) {
                mensaje = "📛 Formato Excel muy antiguo. Abrí el archivo y guardalo como .xlsx.";
            } else {
                mensaje = "❌ Error al procesar el archivo: " + e.getMessage();
            }

            log.error("❌ Error al procesar archivo {}", file.getOriginalFilename(), e);

            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "mensaje", mensaje
            ));
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
                            "Formato Excel muy antiguo (BIFF5). Convertir a .xlsx."
                    );
                }
            }

            throw new IllegalArgumentException("Archivo no soportado: " + originalName);
        }
    }



    //DMS cuando convertía los archivos con format antiguo, no debería necesitarlo mas
//    private File convertirConLibreOffice(File archivoOriginal) throws IOException, InterruptedException {
//        String tempDir = "/tmp";
//
//        ProcessBuilder pb = new ProcessBuilder(
//                "libreoffice", "--headless", "--convert-to", "xlsx",
//                archivoOriginal.getAbsolutePath(), "--outdir", tempDir
//        );
//
//        pb.redirectErrorStream(true);
//        Process process = pb.start();
//        int exitCode = process.waitFor();
//
//        if (exitCode != 0) {
//            throw new IOException("LibreOffice falló al convertir el archivo. Código: " + exitCode);
//        }
//
//        File convertido = new File(tempDir, archivoOriginal.getName().replace(".xls", ".xlsx"));
//        if (!convertido.exists()) {
//            throw new IOException("Archivo convertido no encontrado: " + convertido.getAbsolutePath());
//        }
//
//        return convertido;
//    }
}


    

