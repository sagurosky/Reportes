package plantilla.servicio;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import plantilla.dominio.StockHistorico;
import plantilla.repositorios.StockHistoricoRepository;
import plantilla.repositorios.ProductoRepository;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

        @Autowired
        private ProductoRepository productoRepository;

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
                log.info("Getting robust stock evolution by ambiente from {} to {} for sucursal: {}", fechaInicio,
                                fechaFin, sucursalId);

                Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;

                // 1. Get initial baseline (stock before fechaInicio)
                List<Object[]> initialData = stockHistoricoRepository.findInitialStockByAmbiente(fechaInicio,
                                sucursalIdParam);
                Map<String, Long> currentStockByAmbiente = new HashMap<>();
                for (Object[] row : initialData) {
                        String ambiente = row[0] != null ? (String) row[0] : "Sin Ambiente";
                        Long qty = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                        currentStockByAmbiente.put(ambiente, qty);
                }

                // 2. Get all deltas in range
                List<Object[]> deltaData = stockHistoricoRepository.findDailyStockDeltasByAmbiente(fechaInicio,
                                fechaFin,
                                sucursalIdParam);

                // Group deltas by date and ambiente: Map<Date, Map<Ambiente, Delta>>
                Map<LocalDate, Map<String, Long>> deltasByDate = new HashMap<>();
                for (Object[] row : deltaData) {
                        String ambiente = row[0] != null ? (String) row[0] : "Sin Ambiente";
                        LocalDate fecha = convertToLocalDate(row[1]);
                        Long delta = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                        deltasByDate.computeIfAbsent(fecha, k -> new HashMap<>()).put(ambiente, delta);
                }

                // 3. Reconstruct evolution day by day
                List<StockEvolutionDTO> result = new ArrayList<>();
                List<LocalDate> sortedDates = deltasByDate.keySet().stream().sorted().collect(Collectors.toList());

                // Optimization: if no deltas but we have initial stock, we should at least show
                // the start and end
                // But the chart usually wants all points or at least points where thing
                // changed.
                // However, to be consistent with the user's request, we must show the total
                // stock.
                // We'll iterate through all dates from start to fin to have a continuous line.

                for (LocalDate date = fechaInicio; !date.isAfter(fechaFin); date = date.plusDays(1)) {
                        Map<String, Long> dailyDeltas = deltasByDate.getOrDefault(date, Collections.emptyMap());

                        // Apply today's changes to baseline
                        for (Map.Entry<String, Long> entry : dailyDeltas.entrySet()) {
                                String amb = entry.getKey();
                                Long delta = entry.getValue();
                                currentStockByAmbiente.put(amb, currentStockByAmbiente.getOrDefault(amb, 0L) + delta);
                        }

                        // Record current state for all ambientes seen so far
                        for (Map.Entry<String, Long> entry : currentStockByAmbiente.entrySet()) {
                                result.add(new StockEvolutionDTO(entry.getKey(), date, entry.getValue()));
                        }
                }

                log.info("Calculated evolution with {} data points", result.size());
                return result;
        }

        /**
         * Get stock evolution by familia (drill-down)
         * For detailed line chart when clicking on an ambiente
         */
        public List<StockEvolutionDTO> getStockEvolutionByFamilia(LocalDate fechaInicio, LocalDate fechaFin,
                        Long sucursalId, String ambiente) {
                log.info("Getting robust stock evolution by familia for ambiente '{}' from {} to {}", ambiente,
                                fechaInicio,
                                fechaFin);

                Long sucursalIdParam = sucursalId != null ? sucursalId : -1L;

                // 1. Initial baseline by familia
                List<Object[]> initialData = stockHistoricoRepository.findInitialStockByFamilia(fechaInicio,
                                sucursalIdParam, ambiente);
                Map<String, Long> currentStockByFamilia = new HashMap<>();
                for (Object[] row : initialData) {
                        String familia = row[0] != null ? (String) row[0] : "Sin Familia";
                        Long qty = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                        currentStockByFamilia.put(familia, qty);
                }

                // 2. Daily deltas by familia
                List<Object[]> deltaData = stockHistoricoRepository.findDailyStockDeltasByFamilia(fechaInicio, fechaFin,
                                sucursalIdParam, ambiente);

                Map<LocalDate, Map<String, Long>> deltasByDate = new HashMap<>();
                for (Object[] row : deltaData) {
                        String familia = row[1] != null ? (String) row[1] : "Sin Familia";
                        LocalDate fecha = convertToLocalDate(row[2]);
                        Long delta = row[3] != null ? ((Number) row[3]).longValue() : 0L;
                        deltasByDate.computeIfAbsent(fecha, k -> new HashMap<>()).put(familia, delta);
                }

                // 3. Reconstruct
                List<StockEvolutionDTO> result = new ArrayList<>();
                for (LocalDate date = fechaInicio; !date.isAfter(fechaFin); date = date.plusDays(1)) {
                        Map<String, Long> dailyDeltas = deltasByDate.getOrDefault(date, Collections.emptyMap());

                        for (Map.Entry<String, Long> entry : dailyDeltas.entrySet()) {
                                String fam = entry.getKey();
                                Long delta = entry.getValue();
                                currentStockByFamilia.put(fam, currentStockByFamilia.getOrDefault(fam, 0L) + delta);
                        }

                        for (Map.Entry<String, Long> entry : currentStockByFamilia.entrySet()) {
                                result.add(new StockEvolutionDTO(entry.getKey(), date, entry.getValue()));
                        }
                }

                return result;
        }

        /** Get stock evolution by nivel3 (drill-down: ambiente > familia > nivel3) */
        public List<StockEvolutionDTO> getStockEvolutionByNivel3(LocalDate fechaInicio, LocalDate fechaFin,
                        Long sucursalId, String ambiente, String familia) {
                log.info("Stock evolution by nivel3 for ambiente='{}', familia='{}'", ambiente, familia);
                Long sid = sucursalId != null ? sucursalId : -1L;

                Map<String, Long> current = new HashMap<>();
                for (Object[] row : stockHistoricoRepository.findInitialStockByNivel3(fechaInicio, sid, ambiente,
                                familia)) {
                        current.put(row[0] != null ? (String) row[0] : "Sin Nivel 3",
                                        row[1] != null ? ((Number) row[1]).longValue() : 0L);
                }

                Map<LocalDate, Map<String, Long>> deltas = new HashMap<>();
                for (Object[] row : stockHistoricoRepository.findDailyStockDeltasByNivel3(fechaInicio, fechaFin, sid,
                                ambiente, familia)) {
                        String cat = row[0] != null ? (String) row[0] : "Sin Nivel 3";
                        LocalDate fecha = convertToLocalDate(row[1]);
                        Long delta = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                        deltas.computeIfAbsent(fecha, k -> new HashMap<>()).put(cat, delta);
                }

                List<StockEvolutionDTO> result = new ArrayList<>();
                for (LocalDate date = fechaInicio; !date.isAfter(fechaFin); date = date.plusDays(1)) {
                        deltas.getOrDefault(date, Collections.emptyMap())
                                        .forEach((k, v) -> current.merge(k, v, Long::sum));
                        for (Map.Entry<String, Long> e : current.entrySet())
                                result.add(new StockEvolutionDTO(e.getKey(), date, e.getValue()));
                }
                return result;
        }

        /**
         * Get stock evolution by nivel4 (drill-down: ambiente > familia > nivel3 >
         * nivel4)
         */
        public List<StockEvolutionDTO> getStockEvolutionByNivel4(LocalDate fechaInicio, LocalDate fechaFin,
                        Long sucursalId, String ambiente, String familia, String nivel3) {
                log.info("Stock evolution by nivel4 for nivel3='{}'", nivel3);
                Long sid = sucursalId != null ? sucursalId : -1L;

                Map<String, Long> current = new HashMap<>();
                for (Object[] row : stockHistoricoRepository.findInitialStockByNivel4(fechaInicio, sid, ambiente,
                                familia, nivel3)) {
                        current.put(row[0] != null ? (String) row[0] : "Sin Nivel 4",
                                        row[1] != null ? ((Number) row[1]).longValue() : 0L);
                }

                Map<LocalDate, Map<String, Long>> deltas = new HashMap<>();
                for (Object[] row : stockHistoricoRepository.findDailyStockDeltasByNivel4(fechaInicio, fechaFin, sid,
                                ambiente, familia, nivel3)) {
                        String cat = row[0] != null ? (String) row[0] : "Sin Nivel 4";
                        LocalDate fecha = convertToLocalDate(row[1]);
                        Long delta = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                        deltas.computeIfAbsent(fecha, k -> new HashMap<>()).put(cat, delta);
                }

                List<StockEvolutionDTO> result = new ArrayList<>();
                for (LocalDate date = fechaInicio; !date.isAfter(fechaFin); date = date.plusDays(1)) {
                        deltas.getOrDefault(date, Collections.emptyMap())
                                        .forEach((k, v) -> current.merge(k, v, Long::sum));
                        for (Map.Entry<String, Long> e : current.entrySet())
                                result.add(new StockEvolutionDTO(e.getKey(), date, e.getValue()));
                }
                return result;
        }

        /** Get stock evolution by SKU (deepest drill-down level) */
        public List<StockEvolutionDTO> getStockEvolutionBySku(LocalDate fechaInicio, LocalDate fechaFin,
                        Long sucursalId, String ambiente, String familia, String nivel3, String nivel4) {
                log.info("Stock evolution by sku for nivel4='{}'", nivel4);
                Long sid = sucursalId != null ? sucursalId : -1L;

                Map<String, Long> current = new HashMap<>();
                for (Object[] row : stockHistoricoRepository.findInitialStockBySku(fechaInicio, sid, ambiente, familia,
                                nivel3, nivel4)) {
                        current.put(row[0] != null ? (String) row[0] : "Sin SKU",
                                        row[1] != null ? ((Number) row[1]).longValue() : 0L);
                }

                Map<LocalDate, Map<String, Long>> deltas = new HashMap<>();
                for (Object[] row : stockHistoricoRepository.findDailyStockDeltasBySku(fechaInicio, fechaFin, sid,
                                ambiente, familia, nivel3, nivel4)) {
                        String cat = row[0] != null ? (String) row[0] : "Sin SKU";
                        LocalDate fecha = convertToLocalDate(row[1]);
                        Long delta = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                        deltas.computeIfAbsent(fecha, k -> new HashMap<>()).put(cat, delta);
                }

                List<StockEvolutionDTO> result = new ArrayList<>();
                for (LocalDate date = fechaInicio; !date.isAfter(fechaFin); date = date.plusDays(1)) {
                        deltas.getOrDefault(date, Collections.emptyMap())
                                        .forEach((k, v) -> current.merge(k, v, Long::sum));
                        for (Map.Entry<String, Long> e : current.entrySet())
                                result.add(new StockEvolutionDTO(e.getKey(), date, e.getValue()));
                }
                return result;
        }

        /**
         * Search SKUs by prefix
         */
        public List<String> findSkusByPrefix(String prefix) {
                if (prefix == null)
                        return Collections.emptyList();
                return productoRepository.findSkusByPrefix(prefix.trim(), PageRequest.of(0, 10));
        }

        /**
         * Get stock evolution for a single SKU
         */
        public List<StockEvolutionDTO> getStockEvolutionBySingleSku(LocalDate fechaInicio, LocalDate fechaFin,
                        Long sucursalId, String sku) {
                log.info("Stock evolution for single SKU='{}'", sku);
                Long sid = sucursalId != null ? sucursalId : -1L;

                Map<String, Long> current = new HashMap<>();
                for (Object[] row : stockHistoricoRepository.findInitialStockBySingleSku(fechaInicio, sid, sku)) {
                        current.put(row[0] != null ? (String) row[0] : sku,
                                        row[1] != null ? ((Number) row[1]).longValue() : 0L);
                }

                Map<LocalDate, Map<String, Long>> deltas = new HashMap<>();
                for (Object[] row : stockHistoricoRepository.findDailyStockDeltasBySingleSku(fechaInicio, fechaFin, sid,
                                sku)) {
                        String cat = row[0] != null ? (String) row[0] : sku;
                        LocalDate fecha = convertToLocalDate(row[1]);
                        Long delta = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                        deltas.computeIfAbsent(fecha, k -> new HashMap<>()).put(cat, delta);
                }

                List<StockEvolutionDTO> result = new ArrayList<>();
                for (LocalDate date = fechaInicio; !date.isAfter(fechaFin); date = date.plusDays(1)) {
                        deltas.getOrDefault(date, Collections.emptyMap())
                                        .forEach((k, v) -> current.merge(k, v, Long::sum));
                        for (Map.Entry<String, Long> e : current.entrySet())
                                result.add(new StockEvolutionDTO(e.getKey(), date, e.getValue()));
                }
                return result;
        }

        /**
         * Get stock variation report grouped by ambiente and familia.
         * Returns initial stock, final stock and % variation for each familia within
         * each ambiente.
         * Structure: Map<ambiente, List<[familia, inicial, final, variacion%]>>
         */
        public Map<String, Object> getStockVariacionByAmbienteFamilia(LocalDate fechaInicio, LocalDate fechaFin,
                        Long sucursalId) {
                log.info("Stock variacion by ambiente/familia: {} to {}", fechaInicio, fechaFin);
                Long sid = sucursalId != null ? sucursalId : -1L;

                // Build initial stock map: key = "ambiente|familia"
                Map<String, Long> inicial = new LinkedHashMap<>();
                for (Object[] row : stockHistoricoRepository.findInitialStockByAmbienteFamilia(fechaInicio, sid)) {
                        String amb = row[0] != null ? (String) row[0] : "";
                        String fam = row[1] != null ? (String) row[1] : "";
                        Long qty = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                        inicial.put(amb + "|" + fam, qty);
                }

                // Build final stock map: key = "ambiente|familia"
                Map<String, Long> final_ = new LinkedHashMap<>();
                for (Object[] row : stockHistoricoRepository.findFinalStockByAmbienteFamilia(fechaFin, sid)) {
                        String amb = row[0] != null ? (String) row[0] : "";
                        String fam = row[1] != null ? (String) row[1] : "";
                        Long qty = row[2] != null ? ((Number) row[2]).longValue() : 0L;
                        final_.put(amb + "|" + fam, qty);
                }

                // Combine keys from both maps
                Set<String> allKeys = new LinkedHashSet<>();
                allKeys.addAll(inicial.keySet());
                allKeys.addAll(final_.keySet());

                // Group by ambiente
                Map<String, List<Map<String, Object>>> byAmbiente = new LinkedHashMap<>();
                for (String key : allKeys) {
                        String[] parts = key.split("\\|", 2);
                        String amb = parts[0];
                        String fam = parts.length > 1 ? parts[1] : "";
                        long ini = inicial.getOrDefault(key, 0L);
                        long fin = final_.getOrDefault(key, 0L);
                        double variacion = ini == 0 ? (fin == 0 ? 0.0 : 100.0)
                                        : Math.round(((fin - ini) * 10000.0 / ini)) / 100.0;

                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("familia", fam);
                        row.put("inicial", ini);
                        row.put("final", fin);
                        row.put("variacion", variacion);
                        byAmbiente.computeIfAbsent(amb, k -> new ArrayList<>()).add(row);
                }

                // Build response: list of {ambiente, filas, totales}
                List<Map<String, Object>> ambientes = new ArrayList<>();
                long globalIni = 0;
                long globalFin = 0;

                for (Map.Entry<String, List<Map<String, Object>>> entry : byAmbiente.entrySet()) {
                        String amb = entry.getKey();
                        List<Map<String, Object>> filas = entry.getValue();
                        long totalIni = filas.stream().mapToLong(r -> (Long) r.get("inicial")).sum();
                        long totalFin = filas.stream().mapToLong(r -> (Long) r.get("final")).sum();
                        double totalVar = totalIni == 0 ? (totalFin == 0 ? 0.0 : 100.0)
                                        : Math.round(((totalFin - totalIni) * 10000.0 / totalIni)) / 100.0;

                        globalIni += totalIni;
                        globalFin += totalFin;

                        Map<String, Object> ambMap = new LinkedHashMap<>();
                        ambMap.put("ambiente", amb);
                        ambMap.put("filas", filas);
                        ambMap.put("totalInicial", totalIni);
                        ambMap.put("totalFinal", totalFin);
                        ambMap.put("totalVariacion", totalVar);
                        ambientes.add(ambMap);
                }

                double globalVar = globalIni == 0 ? (globalFin == 0 ? 0.0 : 100.0)
                                : Math.round(((globalFin - globalIni) * 10000.0 / globalIni)) / 100.0;

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("fechaInicio", fechaInicio.toString());
                result.put("fechaFin", fechaFin.toString());
                result.put("ambientes", ambientes);
                result.put("globalTotalInicial", globalIni);
                result.put("globalTotalFinal", globalFin);
                result.put("globalTotalVariacion", globalVar);
                return result;
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

                // Fetch ALL entries for these products to find the one preceding each stockout
                Set<String> skus = stockouts.stream().map(sh -> sh.getProducto().getSku()).collect(Collectors.toSet());
                Map<String, List<StockHistorico>> ingresosByProduct = new HashMap<>(); // Key: Sku|SucursalId
                if (!skus.isEmpty()) {
                        List<StockHistorico> allIngresos = stockHistoricoRepository.findAllIngresosForSkus(skus,
                                        sucursalIdParam);
                        for (StockHistorico li : allIngresos) {
                                String key = li.getProducto().getSku() + "|" + li.getSucursal().getId();
                                ingresosByProduct.computeIfAbsent(key, k -> new ArrayList<>()).add(li);
                        }
                }

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

                        // Find the most recent entry whose ID is smaller than this stockout's ID
                        String key = sh.getProducto().getSku() + "|" + sh.getSucursal().getId();
                        List<StockHistorico> productIngresos = ingresosByProduct.getOrDefault(key,
                                        Collections.emptyList());

                        // productIngresos is sorted by ID DESC in the repo query
                        StockHistorico lastIngreso = productIngresos.stream()
                                        .filter(ing -> ing.getId() < sh.getId())
                                        .findFirst()
                                        .orElse(null);

                        LocalDate fechaUlt = lastIngreso != null ? lastIngreso.getFechaStock() : null;
                        Integer cantUlt = lastIngreso != null
                                        ? (lastIngreso.getCantidad() != null ? lastIngreso.getCantidad().intValue()
                                                        : null)
                                        : null;

                        Integer agregado = lastIngreso != null
                                        ? (lastIngreso.getCantidad() != null && lastIngreso.getDiffAnterior() != null
                                                        ? Integer.valueOf(lastIngreso.getCantidad().intValue()
                                                                        - lastIngreso.getDiffAnterior().intValue())
                                                        : null)
                                        : null;

                        StockoutDayDTO.ProductoStockoutInfo info = new StockoutDayDTO.ProductoStockoutInfo(
                                        sh.getProducto().getSku(),
                                        sh.getProducto().getDescripcion(),
                                        sh.getProducto().getAmbiente(),
                                        sh.getProducto().getFamilia(),
                                        sh.getSucursal().getNombre(),
                                        sh.getFechaStock(),
                                        fechaUlt,
                                        cantUlt,
                                        agregado);

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

                // Get stock evolution (using our new robust method)
                List<StockEvolutionDTO> stockEvolution = getStockEvolutionByAmbiente(fechaInicio, fechaFin,
                                sucursalId);

                // Aggregate by date (summing all ambientes)
                Map<LocalDate, Long> stockByDate = stockEvolution.stream()
                                .collect(Collectors.groupingBy(
                                                StockEvolutionDTO::getFecha,
                                                Collectors.summingLong(StockEvolutionDTO::getCantidad)));

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
         * Export stockouts to Excel file
         */
        public byte[] exportStockoutsToExcel(Long sucursalId, LocalDate fechaInicio) throws IOException {
                log.info("Exporting stockouts to Excel. Sucursal: {}, Since: {}", sucursalId, fechaInicio);
                List<StockoutDayDTO> data = getStockoutsByDayOfWeek(sucursalId, fechaInicio);

                try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        Sheet sheet = workbook.createSheet("Análisis de Quiebres");

                        // Styles
                        CellStyle headerStyle = workbook.createCellStyle();
                        Font headerFont = workbook.createFont();
                        headerFont.setBold(true);
                        headerStyle.setFont(headerFont);
                        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                        // Header Row
                        Row headerRow = sheet.createRow(0);
                        String[] columns = { "Día", "SKU", "Descripción", "Ambiente", "Familia", "Sucursal",
                                        "Fecha Quiebre", "Fecha ultimo Ingreso", "Total ultimo ingreso", "Agregado" };
                        for (int i = 0; i < columns.length; i++) {
                                Cell cell = headerRow.createCell(i);
                                cell.setCellValue(columns[i]);
                                cell.setCellStyle(headerStyle);
                        }

                        // Data Rows
                        int rowIdx = 1;
                        for (StockoutDayDTO day : data) {
                                for (StockoutDayDTO.ProductoStockoutInfo p : day.getProductos()) {
                                        Row row = sheet.createRow(rowIdx++);
                                        row.createCell(0).setCellValue(day.getDiaNombre());
                                        row.createCell(1).setCellValue(p.getSku());
                                        row.createCell(2).setCellValue(p.getDescripcion());
                                        row.createCell(3).setCellValue(p.getAmbiente());
                                        row.createCell(4).setCellValue(p.getFamilia());
                                        row.createCell(5).setCellValue(p.getSucursal());

                                        String fechaStr = p.getFechaStock() != null
                                                        ? p.getFechaStock().format(
                                                                        DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                                        : "";
                                        row.createCell(6).setCellValue(fechaStr);

                                        String fechaUltStr = p.getFechaUltimoIngreso() != null
                                                        ? p.getFechaUltimoIngreso().format(
                                                                        DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                                        : "-";
                                        row.createCell(7).setCellValue(fechaUltStr);

                                        if (p.getCantidadUltimoIngreso() != null) {
                                                row.createCell(8).setCellValue(p.getCantidadUltimoIngreso());
                                        } else {
                                                row.createCell(8).setCellValue("-");
                                        }

                                        if (p.getAgregado() != null) {
                                                row.createCell(9).setCellValue(p.getAgregado());
                                        } else {
                                                row.createCell(9).setCellValue("-");
                                        }
                                }
                        }

                        // Auto-size columns
                        for (int i = 0; i < columns.length; i++) {
                                sheet.autoSizeColumn(i);
                        }

                        workbook.write(out);
                        return out.toByteArray();
                }
        }

        /**
         * Export variacion de stock por ambiente/familia to Excel
         */
        public byte[] exportVariacionToExcel(LocalDate fechaInicio, LocalDate fechaFin, Long sucursalId)
                        throws IOException {
                log.info("Exporting variacion stock to Excel. {} to {}", fechaInicio, fechaFin);

                @SuppressWarnings("unchecked")
                Map<String, Object> data = getStockVariacionByAmbienteFamilia(fechaInicio, fechaFin, sucursalId);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> ambientes = (List<Map<String, Object>>) data.get("ambientes");

                try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        Sheet sheet = workbook.createSheet("Variación por Ambiente");

                        // Styles
                        CellStyle headerStyle = workbook.createCellStyle();
                        Font headerFont = workbook.createFont();
                        headerFont.setBold(true);
                        headerStyle.setFont(headerFont);
                        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                        CellStyle totalStyle = workbook.createCellStyle();
                        Font totalFont = workbook.createFont();
                        totalFont.setBold(true);
                        totalStyle.setFont(totalFont);
                        totalStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
                        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                        // Header Row
                        String[] columns = { "Ambiente", "Familia", "Cant. Inicial", "Cant. Final", "Variación %",
                                        "Fecha Inicio", "Fecha Fin" };
                        Row headerRow = sheet.createRow(0);
                        for (int i = 0; i < columns.length; i++) {
                                Cell cell = headerRow.createCell(i);
                                cell.setCellValue(columns[i]);
                                cell.setCellStyle(headerStyle);
                        }

                        // Data Rows
                        int rowIdx = 1;
                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                        String fIni = fechaInicio.format(fmt);
                        String fFin = fechaFin.format(fmt);

                        for (Map<String, Object> amb : ambientes) {
                                String ambiente = (String) amb.get("ambiente");
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> filas = (List<Map<String, Object>>) amb.get("filas");

                                for (Map<String, Object> fila : filas) {
                                        Row row = sheet.createRow(rowIdx++);
                                        row.createCell(0).setCellValue(ambiente);
                                        row.createCell(1).setCellValue((String) fila.get("familia"));
                                        row.createCell(2).setCellValue(((Number) fila.get("inicial")).longValue());
                                        row.createCell(3).setCellValue(((Number) fila.get("final")).longValue());
                                        row.createCell(4).setCellValue(((Number) fila.get("variacion")).doubleValue());
                                        row.createCell(5).setCellValue(fIni);
                                        row.createCell(6).setCellValue(fFin);
                                }

                                // Subtotal row per ambiente
                                Row totalRow = sheet.createRow(rowIdx++);
                                totalRow.createCell(0).setCellValue(ambiente);
                                Cell totalLabelCell = totalRow.createCell(1);
                                totalLabelCell.setCellValue("TOTAL");
                                totalLabelCell.setCellStyle(totalStyle);
                                Cell totalIniCell = totalRow.createCell(2);
                                totalIniCell.setCellValue(((Number) amb.get("totalInicial")).longValue());
                                totalIniCell.setCellStyle(totalStyle);
                                Cell totalFinCell = totalRow.createCell(3);
                                totalFinCell.setCellValue(((Number) amb.get("totalFinal")).longValue());
                                totalFinCell.setCellStyle(totalStyle);
                                Cell totalVarCell = totalRow.createCell(4);
                                totalVarCell.setCellValue(((Number) amb.get("totalVariacion")).doubleValue());
                                totalVarCell.setCellStyle(totalStyle);
                                totalRow.createCell(5).setCellValue(fIni);
                                totalRow.createCell(6).setCellValue(fFin);
                        }

                        // Global Total Row
                        Row globalTotalRow = sheet.createRow(rowIdx++);
                        Cell gtLabelCell = globalTotalRow.createCell(1);
                        gtLabelCell.setCellValue("TOTAL GENERAL");
                        gtLabelCell.setCellStyle(totalStyle);

                        Cell gtIniCell = globalTotalRow.createCell(2);
                        gtIniCell.setCellValue(((Number) data.get("globalTotalInicial")).longValue());
                        gtIniCell.setCellStyle(totalStyle);

                        Cell gtFinCell = globalTotalRow.createCell(3);
                        gtFinCell.setCellValue(((Number) data.get("globalTotalFinal")).longValue());
                        gtFinCell.setCellStyle(totalStyle);

                        Cell gtVarCell = globalTotalRow.createCell(4);
                        gtVarCell.setCellValue(((Number) data.get("globalTotalVariacion")).doubleValue());
                        gtVarCell.setCellStyle(totalStyle);

                        globalTotalRow.createCell(5).setCellValue(fIni);
                        globalTotalRow.createCell(6).setCellValue(fFin);

                        // Auto-size columns
                        for (int i = 0; i < columns.length; i++) {
                                sheet.autoSizeColumn(i);
                        }

                        workbook.write(out);
                        return out.toByteArray();
                }
        }

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

        private LocalDate convertToLocalDate(Object dateObj) {
                if (dateObj == null)
                        return LocalDate.now();
                if (dateObj instanceof java.sql.Date) {
                        return ((java.sql.Date) dateObj).toLocalDate();
                } else if (dateObj instanceof java.sql.Timestamp) {
                        return ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                } else if (dateObj instanceof LocalDate) {
                        return (LocalDate) dateObj;
                }
                return LocalDate.now();
        }
}
