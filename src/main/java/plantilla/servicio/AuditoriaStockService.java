package plantilla.servicio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import plantilla.repositorios.EventoCargaRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Service for Stock Audit Dashboard
 * Provides KPIs, compliance tracking, and event listing
 */
@Service
@Slf4j
public class AuditoriaStockService {

    @Autowired
    private EventoCargaRepository eventoCargaRepository;

    /**
     * Get KPIs for audit dashboard
     */
    public AuditoriaKPIsDTO getKPIs(LocalDate fechaInicio, LocalDate fechaFin, Long sucursalId) {
        log.info("Getting audit KPIs from {} to {} for sucursal: {}", fechaInicio, fechaFin, sucursalId);

        Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;

        Long totalCargas = eventoCargaRepository.countTotalCargas(fechaInicio, fechaFin, sucursalIdParam);
        Long cargasExitosas = eventoCargaRepository.countCargasExitosas(fechaInicio, fechaFin, sucursalIdParam);
        Long conErrores = totalCargas - cargasExitosas;
        Double promedio = eventoCargaRepository.getPromedioCompletado(fechaInicio, fechaFin, sucursalIdParam);

        // Handle null promedio (no data)
        if (promedio == null) {
            promedio = 0.0;
        }

        return new AuditoriaKPIsDTO(totalCargas, cargasExitosas, conErrores, promedio);
    }

    /**
     * Get daily compliance data with business rule validation
     */
    public List<CumplimientoDiarioDTO> getCumplimientoDiario(LocalDate fechaInicio, LocalDate fechaFin,
            Long sucursalId) {
        log.info("Getting daily compliance from {} to {} for sucursal: {}", fechaInicio, fechaFin, sucursalId);

        Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;
        List<Object[]> data = eventoCargaRepository.findCumplimientoDiario(fechaInicio, fechaFin, sucursalIdParam);

        return data.stream()
                .map(row -> {
                    // Parse data from query result
                    LocalDate fecha = convertToLocalDate(row[0]);
                    String sucursalNombre = (String) row[1];
                    String updStockDia = (String) row[2]; // Expected day from sucursales.upd_stock
                    Integer cantidadCargas = ((Number) row[3]).intValue();

                    // Calculate day of week name in Spanish
                    String diaEsperado = fecha.getDayOfWeek()
                            .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
                    // Capitalize first letter
                    diaEsperado = diaEsperado.substring(0, 1).toUpperCase() + diaEsperado.substring(1);

                    // Business Rule: Volume indicator
                    Boolean estadoVolumen = cantidadCargas > 2;

                    // Business Rule: Compliance validation
                    Boolean cumpleRegla = evaluarCumplimiento(fecha, updStockDia, cantidadCargas);

                    return new CumplimientoDiarioDTO(
                            fecha,
                            sucursalNombre,
                            diaEsperado,
                            cantidadCargas,
                            estadoVolumen,
                            cumpleRegla,
                            updStockDia);
                })
                .collect(Collectors.toList());
    }

    /**
     * Get recent event loads for audit table
     */
    public List<EventoCargaDTO> getEventosCarga(LocalDate fechaInicio, LocalDate fechaFin, Long sucursalId) {
        log.info("Getting event loads from {} to {} for sucursal: {}", fechaInicio, fechaFin, sucursalId);

        Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;
        List<Object[]> data = eventoCargaRepository.findEventosCarga(fechaInicio, fechaFin, sucursalIdParam);

        return data.stream()
                .map(row -> {
                    // Parse data from query result
                    java.sql.Timestamp timestamp = (java.sql.Timestamp) row[0];
                    java.time.LocalDateTime fecha = timestamp.toLocalDateTime();
                    String sucursalNombre = (String) row[1];
                    String usuario = (String) row[2];
                    String nombreArchivo = (String) row[3];
                    Integer procesados = row[4] != null ? ((Number) row[4]).intValue() : 0;
                    Integer totalRegistros = row[5] != null ? ((Number) row[5]).intValue() : 0;
                    Integer porcentaje = row[6] != null ? ((Number) row[6]).intValue() : 0;
                    String estado = (String) row[7];

                    return new EventoCargaDTO(
                            fecha,
                            sucursalNombre,
                            usuario,
                            nombreArchivo,
                            procesados,
                            totalRegistros,
                            porcentaje,
                            estado);
                })
                .collect(Collectors.toList());
    }

    /**
     * Business Rule: Evaluate compliance based on day of week and upd_stock
     * 
     * - If current day matches upd_stock day: requires >= 2 cargas
     * - If current day does NOT match upd_stock day: requires > 1 carga
     */
    private Boolean evaluarCumplimiento(LocalDate fecha, String updStockDia, Integer cantidadCargas) {
        if (updStockDia == null || updStockDia.trim().isEmpty()) {
            // No upd_stock configured, use default rule: > 1
            return cantidadCargas > 1;
        }

        // Get day of week name in Spanish
        String diaActual = fecha.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        // Capitalize first letter to match potential database values
        diaActual = diaActual.substring(0, 1).toUpperCase() + diaActual.substring(1);

        // Compare (case-insensitive)
        boolean esDiaCargaFuerte = diaActual.equalsIgnoreCase(updStockDia.trim());

        if (esDiaCargaFuerte) {
            // Scenario B: Heavy load day - requires at least 2 cargas
            return cantidadCargas >= 2;
        } else {
            // Scenario A: Normal day - requires more than 1 carga
            return cantidadCargas > 1;
        }
    }

    /**
     * Helper method to convert various date types to LocalDate
     */
    private LocalDate convertToLocalDate(Object dateObj) {
        if (dateObj instanceof java.sql.Date) {
            return ((java.sql.Date) dateObj).toLocalDate();
        } else if (dateObj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
        } else if (dateObj instanceof LocalDate) {
            return (LocalDate) dateObj;
        }
        log.warn("Unknown date type in compliance query: {}", dateObj != null ? dateObj.getClass().getName() : "null");
        return LocalDate.now(); // fallback
    }
}
