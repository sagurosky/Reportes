package plantilla.servicio;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import plantilla.dominio.EventoCarga;

import java.io.InputStream;
import java.time.LocalDate;

@Service
@Slf4j
public class StockAsyncService {

    @Autowired
    private StockService stockService;

    @Autowired
    private EventoCargaService eventoCargaService;
    @Autowired
    S3Service s3Service;
    @Async
    public void procesarAsync(
            Sheet sheet,
            String nombreArchivo,
            LocalDate fechaStock,
            Long eventoId
    ) {
        try {
            log.info("🚀 Inicio procesamiento async evento {}", eventoId);

            EventoCarga evento = eventoCargaService.buscarPorId(eventoId);

            stockService.procesarStock(
                    sheet,
                    nombreArchivo,
                    fechaStock,
                    evento
            );

            EventoCarga eventoFinal =
                    eventoCargaService.buscarPorId(eventoId);

            eventoCargaService.marcarCompletado(eventoFinal);

            log.info("✅ Procesamiento finalizado evento {}", eventoFinal);

        } catch (Exception e) {

            log.error("❌ Error en procesamiento async evento {}", eventoId, e);

            eventoCargaService.marcarFallido(eventoId, e.getMessage());
        }
    }

    @Async
    public void procesarDesdeS3(Long eventoId, LocalDate fechaStock) {

        EventoCarga evento = eventoCargaService.buscarPorId(eventoId);

        try (InputStream is = s3Service.descargar(evento.getRutaS3())) {

            Workbook workbook = new XSSFWorkbook(is);
            Sheet sheet = workbook.getSheetAt(0);

            stockService.procesarStock(
                    sheet,
                    evento.getNombreArchivo(),
                    fechaStock,
                    evento
            );

        } catch (Exception e) {
            eventoCargaService.marcarFallido(eventoId, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}