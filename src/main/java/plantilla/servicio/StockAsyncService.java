package plantilla.servicio;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import plantilla.dominio.EventoCarga;

import java.time.LocalDate;

@Service
@Slf4j
public class StockAsyncService {

    @Autowired
    private StockService stockService;

    @Autowired
    private EventoCargaService eventoCargaService;

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
}