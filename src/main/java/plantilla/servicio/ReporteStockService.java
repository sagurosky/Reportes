package plantilla.servicio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import plantilla.dominio.StockHistorico;
import plantilla.repositorios.StockHistoricoRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for Stock Analytics Dashboard
 * Provides data aggregation for charts and reports
 */
@Service
@Slf4j
public class ReporteStockService {

        @Autowired
        private StockHistoricoRepository stockHistoricoRepository;

        /**
         * Get current stock snapshot grouped by ambiente and familia
         * For treemap and summary statistics
         */
        /**
         * Get current stock snapshot grouped by hierarchy
         * Returns flat list for frontend processing
         */
        public List<StockSnapshotDTO> getStockSnapshot(Long sucursalId) {
                log.info("Getting stock snapshot for sucursal: {}", sucursalId);

                Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;
                List<StockHistorico> latestStock = stockHistoricoRepository.findLatestStockBySucursal(sucursalIdParam);

                // Map to DTOs while aggregating quantities
                // Key: Ambiente-Familia-Nivel3-Nivel4
                Map<String, StockSnapshotDTO> aggregated = new HashMap<>();

                for (StockHistorico sh : latestStock) {
                        String ambiente = sh.getProducto().getAmbiente() != null ? sh.getProducto().getAmbiente()
                                        : "Sin Ambiente";
                        String familia = sh.getProducto().getFamilia() != null ? sh.getProducto().getFamilia()
                                        : "Sin Familia";
                        String nivel3 = sh.getProducto().getNivel3() != null ? sh.getProducto().getNivel3()
                                        : "Sin Nivel 3";
                        String nivel4 = sh.getProducto().getNivel4() != null ? sh.getProducto().getNivel4()
                                        : "Sin Nivel 4";

                        String key = String.format("%s|%s|%s|%s", ambiente, familia, nivel3, nivel4);
                        Long qty = sh.getCantidad() != null ? sh.getCantidad() : 0L;

                        aggregated.compute(key, (k, v) -> {
                                if (v == null) {
                                        return new StockSnapshotDTO(ambiente, familia, nivel3, nivel4, qty);
                                } else {
                                        v.setCantidad(v.getCantidad() + qty);
                                        return v;
                                }
                        });
                }

                return new ArrayList<>(aggregated.values());
        }

        /**
         * Get stock evolution over time by ambiente
         * For line chart visualization
         */
        public List<StockEvolutionDTO> getStockEvolutionByAmbiente(LocalDate fechaInicio, LocalDate fechaFin,
                        Long sucursalId) {
                log.info("Getting stock evolution by ambiente from {} to {} for sucursal: {}", fechaInicio, fechaFin,
                                sucursalId);

                Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;
                List<Object[]> data = stockHistoricoRepository.findStockEvolutionByAmbiente(fechaInicio, fechaFin,
                                sucursalIdParam);

                log.info("Retrieved {} evolution data points", data.size());

                return data.stream()
                                .map(row -> {
                                        String ambiente = row[0] != null ? (String) row[0] : "Sin Ambiente";

                                        // Native query returns java.sql.Date, need to convert to LocalDate
                                        LocalDate fecha;
                                        Object dateObj = row[1];
                                        if (dateObj instanceof java.sql.Date) {
                                                fecha = ((java.sql.Date) dateObj).toLocalDate();
                                        } else if (dateObj instanceof java.sql.Timestamp) {
                                                fecha = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                                        } else if (dateObj instanceof LocalDate) {
                                                fecha = (LocalDate) dateObj;
                                        } else {
                                                fecha = LocalDate.now(); // fallback
                                        }

                                        Long cantidad = row[2] != null ? ((Number) row[2]).longValue() : 0L;

                                        return new StockEvolutionDTO(ambiente, fecha, cantidad);
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Get stock evolution by familia (drill-down)
         * For detailed line chart when clicking on an ambiente
         */
        public List<StockEvolutionDTO> getStockEvolutionByFamilia(LocalDate fechaInicio, LocalDate fechaFin,
                        Long sucursalId, String ambiente) {
                log.info("Getting stock evolution by familia for ambiente '{}' from {} to {}", ambiente, fechaInicio,
                                fechaFin);

                Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;
                List<Object[]> data = stockHistoricoRepository.findStockEvolutionByFamilia(fechaInicio, fechaFin,
                                sucursalIdParam,
                                ambiente);

                return data.stream()
                                .map(row -> {
                                        String familia = row[1] != null ? (String) row[1] : "Sin Familia";

                                        // Native query returns java.sql.Date
                                        LocalDate fecha;
                                        Object dateObj = row[2];
                                        if (dateObj instanceof java.sql.Date) {
                                                fecha = ((java.sql.Date) dateObj).toLocalDate();
                                        } else if (dateObj instanceof java.sql.Timestamp) {
                                                fecha = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                                        } else if (dateObj instanceof LocalDate) {
                                                fecha = (LocalDate) dateObj;
                                        } else {
                                                fecha = LocalDate.now(); // fallback
                                        }

                                        Long cantidad = row[3] != null ? ((Number) row[3]).longValue() : 0L;

                                        return new StockEvolutionDTO(familia, fecha, cantidad);
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Get raw treemap data for frontend visualization
         * Now returns the flat list directly
         */
        public List<StockSnapshotDTO> getTreemapData(Long sucursalId) {
                log.info("Returning raw treemap data for sucursal: {}", sucursalId);
                return getStockSnapshot(sucursalId);
        }

        /**
         * Get stockouts grouped by day of week
         * For accordion table showing which products ran out on which days
         */
        public List<StockoutDayDTO> getStockoutsByDayOfWeek(Long sucursalId, LocalDate fechaInicio) {
                log.info("Getting stockouts by day of week for sucursal: {} since {}", sucursalId, fechaInicio);

                Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;
                List<StockHistorico> stockouts = stockHistoricoRepository.findStockouts(fechaInicio, sucursalIdParam);

                // Initialize all days of the week
                Map<DayOfWeek, StockoutDayDTO> dayMap = new LinkedHashMap<>();
                for (DayOfWeek day : DayOfWeek.values()) {
                        String dayName = getDayNameInSpanish(day);
                        dayMap.put(day, new StockoutDayDTO(day, dayName));
                }

                // Group stockouts by day of week
                stockouts.forEach(sh -> {
                        DayOfWeek day = sh.getFechaStock().getDayOfWeek();
                        StockoutDayDTO dayDTO = dayMap.get(day);

                        StockoutDayDTO.ProductoStockoutInfo info = new StockoutDayDTO.ProductoStockoutInfo(
                                        sh.getProducto().getSku(),
                                        sh.getProducto().getDescripcion(),
                                        sh.getProducto().getAmbiente(),
                                        sh.getProducto().getFamilia(),
                                        sh.getSucursal().getNombre(),
                                        sh.getFechaStock());

                        dayDTO.getProductos().add(info);
                        dayDTO.setProductoCount(dayDTO.getProductoCount() + 1);
                });

                // Return in week order (Sunday-Monday-...-Saturday)
                return Arrays.asList(
                                dayMap.get(DayOfWeek.SUNDAY),
                                dayMap.get(DayOfWeek.MONDAY),
                                dayMap.get(DayOfWeek.TUESDAY),
                                dayMap.get(DayOfWeek.WEDNESDAY),
                                dayMap.get(DayOfWeek.THURSDAY),
                                dayMap.get(DayOfWeek.FRIDAY),
                                dayMap.get(DayOfWeek.SATURDAY));
        }

        /**
         * Get consumption vs current stock comparison
         * For bar chart showing consumption trends vs available stock
         */
        public List<ConsumoStockDTO> getConsumoVsStock(LocalDate fechaInicio, LocalDate fechaFin, Long sucursalId) {
                log.info("Getting consumption vs stock from {} to {} for sucursal: {}", fechaInicio, fechaFin,
                                sucursalId);

                // Get consumption data
                Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;
                List<Object[]> consumoData = stockHistoricoRepository.findConsumoAnalysis(fechaInicio, fechaFin,
                                sucursalIdParam);

                // Group by date - handle potential java.sql.Date, java.sql.Timestamp or
                // LocalDate
                Map<LocalDate, Long> consumoByDate = consumoData.stream()
                                .collect(Collectors.groupingBy(
                                                row -> {
                                                        Object dateObj = row[3];
                                                        if (dateObj instanceof java.sql.Date) {
                                                                return ((java.sql.Date) dateObj).toLocalDate();
                                                        } else if (dateObj instanceof java.sql.Timestamp) {
                                                                return ((java.sql.Timestamp) dateObj).toLocalDateTime()
                                                                                .toLocalDate();
                                                        } else if (dateObj instanceof LocalDate) {
                                                                return (LocalDate) dateObj;
                                                        }
                                                        log.warn("Unknown date type in consumption query: {}",
                                                                        dateObj != null ? dateObj.getClass().getName()
                                                                                        : "null");
                                                        return LocalDate.now(); // fallback
                                                },
                                                Collectors.summingLong(
                                                                row -> row[2] != null ? ((Number) row[2]).longValue()
                                                                                : 0L)));

                // Get stock evolution
                List<Object[]> stockData = stockHistoricoRepository.findStockEvolutionByAmbiente(fechaInicio, fechaFin,
                                sucursalIdParam);

                // Group by date - handle potential java.sql.Date, java.sql.Timestamp or
                // LocalDate
                Map<LocalDate, Long> stockByDate = stockData.stream()
                                .collect(Collectors.groupingBy(
                                                row -> {
                                                        Object dateObj = row[1];
                                                        if (dateObj instanceof java.sql.Date) {
                                                                return ((java.sql.Date) dateObj).toLocalDate();
                                                        } else if (dateObj instanceof java.sql.Timestamp) {
                                                                return ((java.sql.Timestamp) dateObj).toLocalDateTime()
                                                                                .toLocalDate();
                                                        } else if (dateObj instanceof LocalDate) {
                                                                return (LocalDate) dateObj;
                                                        }
                                                        return LocalDate.now(); // fallback
                                                },
                                                Collectors.summingLong(
                                                                row -> row[2] != null ? ((Number) row[2]).longValue()
                                                                                : 0L)));

                // Merge both datasets
                Set<LocalDate> allDates = new TreeSet<>();
                allDates.addAll(consumoByDate.keySet());
                allDates.addAll(stockByDate.keySet());

                log.info("Generated {} data points for consumo vs stock chart", allDates.size());

                return allDates.stream()
                                .map(fecha -> new ConsumoStockDTO(
                                                fecha,
                                                consumoByDate.getOrDefault(fecha, 0L),
                                                stockByDate.getOrDefault(fecha, 0L)))
                                .sorted(Comparator.comparing(ConsumoStockDTO::getFecha))
                                .collect(Collectors.toList());
        }

        /**
         * Helper method to get Spanish day names
         */
        private String getDayNameInSpanish(DayOfWeek day) {
                return switch (day) {
                        case MONDAY -> "Lunes";
                        case TUESDAY -> "Martes";
                        case WEDNESDAY -> "Miércoles";
                        case THURSDAY -> "Jueves";
                        case FRIDAY -> "Viernes";
                        case SATURDAY -> "Sábado";
                        case SUNDAY -> "Domingo";
                };
        }
}
