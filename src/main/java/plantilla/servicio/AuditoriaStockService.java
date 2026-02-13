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

    @Autowired
    private plantilla.repositorios.SucursalRepository sucursalRepository;

    /**
     * Get KPIs for audit dashboard
     */
    public AuditoriaKPIsDTO getKPIs(LocalDate fechaInicio, LocalDate fechaFin, Long sucursalId) {
        log.info("Getting audit KPIs from {} to {} for sucursal: {}", fechaInicio, fechaFin, sucursalId);

        Long totalCargas = eventoCargaRepository.countTotalCargas(fechaInicio, fechaFin, sucursalId);
        Long cargasExitosas = eventoCargaRepository.countCargasExitosas(fechaInicio, fechaFin, sucursalId);
        Long conErrores = totalCargas - cargasExitosas;
        Double promedio = eventoCargaRepository.getPromedioCompletado(fechaInicio, fechaFin, sucursalId);

        // Handle null promedio (no data)
        if (promedio == null) {
            promedio = 0.0;
        }

        return new AuditoriaKPIsDTO(totalCargas, cargasExitosas, conErrores, promedio);
    }

    /**
     * Get daily compliance data with business rule validation
     * Generates records for all days and sucursales in range
     */
    public List<CumplimientoDiarioDTO> getCumplimientoDiario(LocalDate fechaInicio, LocalDate fechaFin,
            Long sucursalId) {
        log.info("Getting daily compliance from {} to {} for sucursal: {}", fechaInicio, fechaFin, sucursalId);

        // 1. Get existing data from DB
        List<Object[]> dbData = eventoCargaRepository.findCumplimientoDiario(fechaInicio, fechaFin, sucursalId);

        // Group DB data by fecha and sucursalNombre for easy lookup
        java.util.Map<String, Integer> loadCounts = new java.util.HashMap<>();
        for (Object[] row : dbData) {
            LocalDate fecha = convertToLocalDate(row[0]);
            String sucursalNombre = (String) row[1];
            Integer cantidad = ((Number) row[3]).intValue();
            log.info("DB Data found: {} - {} -> {} loads", fecha, sucursalNombre, cantidad);
            loadCounts.put(fecha.toString() + "_" + sucursalNombre, cantidad);
        }

        // 2. Get target sucursales
        List<plantilla.dominio.Sucursal> sucursales;
        if (sucursalId != null) {
            sucursales = sucursalRepository.findById(sucursalId)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            sucursales = sucursalRepository.findByInhabilitadoFalse();
        }

        // 3. Generate all records
        List<CumplimientoDiarioDTO> result = new java.util.ArrayList<>();

        for (LocalDate date = fechaFin; !date.isBefore(fechaInicio); date = date.minusDays(1)) {
            for (plantilla.dominio.Sucursal suc : sucursales) {
                String key = date.toString() + "_" + suc.getNombre();
                Integer cantidadCargas = loadCounts.getOrDefault(key, 0);

                String updStockDia = suc.getUpdStock();

                // Calculate display day of week
                String diaDisplay = date.getDayOfWeek()
                        .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
                diaDisplay = diaDisplay.substring(0, 1).toUpperCase() + diaDisplay.substring(1);

                // Business Rule: Compliance validation
                Boolean cumpleRegla = evaluarCumplimiento(date, updStockDia, cantidadCargas);

                // Business Rule: Volumen OK if rule is met
                Boolean estadoVolumen = cumpleRegla;

                result.add(new CumplimientoDiarioDTO(
                        date,
                        suc.getNombre(),
                        diaDisplay,
                        cantidadCargas,
                        estadoVolumen,
                        cumpleRegla,
                        updStockDia));
            }
        }

        return result;
    }

    /**
     * Get recent event loads for audit table
     */
    public List<EventoCargaDTO> getEventosCarga(LocalDate fechaInicio, LocalDate fechaFin, Long sucursalId) {
        log.info("Getting event loads from {} to {} for sucursal: {}", fechaInicio, fechaFin, sucursalId);

        List<Object[]> data = eventoCargaRepository.findEventosCarga(fechaInicio, fechaFin, sucursalId);

        return data.stream()
                .map(row -> {
                    // Parse data from query result
                    java.time.LocalDateTime fecha = null;
                    if (row[0] instanceof java.sql.Timestamp) {
                        fecha = ((java.sql.Timestamp) row[0]).toLocalDateTime();
                    } else if (row[0] instanceof java.time.LocalDateTime) {
                        fecha = (java.time.LocalDateTime) row[0];
                    }

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
     * - If current day DOES NOT match upd_stock day: OK if cargas >= 1
     * - If current day DOES match upd_stock day: OK if cargas >= 2
     */
    private Boolean evaluarCumplimiento(LocalDate fecha, String updStockDia, Integer cantidadCargas) {
        // Get day of week name in Spanish
        String diaActual = fecha.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));

        // Normalize for comparison
        boolean esDiaCargaFuerte = updStockDia != null && diaActual.equalsIgnoreCase(updStockDia.trim());

        if (esDiaCargaFuerte) {
            // Scenario: Heavy load day - requires at least 2 cargas
            return cantidadCargas >= 2;
        } else {
            // Scenario: Normal day - requires at least 1 carga
            return cantidadCargas >= 1;
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
