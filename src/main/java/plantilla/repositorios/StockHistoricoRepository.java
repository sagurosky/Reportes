package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import plantilla.dominio.Producto;
import plantilla.dominio.StockHistorico;

import plantilla.dominio.Sucursal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockHistoricoRepository extends JpaRepository<StockHistorico, Long> {

        Optional<StockHistorico> findTopByProducto_SkuAndSucursalOrderByFechaStockDescIdDesc(
                        String sku,
                        Sucursal sucursal);

        List<StockHistorico> findByProductoAndSucursalOrderByFechaStockAsc(
                        Producto producto,
                        Sucursal sucursal);

        boolean existsByProductoAndSucursalAndFechaStock(
                        Producto producto,
                        Sucursal sucursal,
                        LocalDate fechaStock);

        // Dashboard Queries - Based on SQL reference requirements

        /**
         * Query 1: Stock Actual Snapshot
         * Gets the latest stock record for each producto in a sucursal
         * Based on: SELECT DISTINCT ON (producto_id, sucursal_id) ... ORDER BY
         * fecha_stock DESC
         */
        @Query(value = """
                        SELECT sh.*
                        FROM stock_historico sh
                        WHERE sh.id IN (
                            SELECT MAX(sh2.id)
                            FROM stock_historico sh2
                            WHERE (:sucursalId = -1 OR sh2.sucursal_id = :sucursalId)
                            GROUP BY sh2.producto_id, sh2.sucursal_id
                        )
                        ORDER BY sh.id
                        """, nativeQuery = true)
        List<StockHistorico> findLatestStockBySucursal(@Param("sucursalId") Long sucursalId);

        /**
         * Query 2: Consumption Analysis (Egresos)
         * Analyzes consumption by ambiente and familia over time.
         * Fixed logic: Calculates consumption as (Previous Stock - Current Stock)
         * for records where stock decreased.
         */
        @Query(value = """
                        SELECT p.ambiente,
                               p.familia,
                               SUM(sh.diff_anterior - sh.cantidad),
                               sh.fecha_stock
                        FROM stock_historico sh
                        JOIN productos p ON sh.producto_id = p.id
                        WHERE sh.nuevo_ingreso = false
                          AND sh.diff_anterior IS NOT NULL
                          AND sh.cantidad < sh.diff_anterior
                          AND sh.fecha_stock BETWEEN :fechaInicio AND :fechaFin
                          AND (:sucursalId = -1 OR sh.sucursal_id = :sucursalId)
                        GROUP BY p.ambiente, p.familia, sh.fecha_stock
                        ORDER BY sh.fecha_stock ASC
                        """, nativeQuery = true)
        List<Object[]> findConsumoAnalysis(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("fechaFin") LocalDate fechaFin,
                        @Param("sucursalId") Long sucursalId);

        /**
         * Get initial stock snapshot before a specific date for evolution baseline
         */
        @Query(value = """
                        SELECT p.ambiente, SUM(sh.cantidad)
                        FROM stock_historico sh
                        JOIN productos p ON sh.producto_id = p.id
                        WHERE sh.id IN (
                            SELECT MAX(sh2.id)
                            FROM stock_historico sh2
                            WHERE sh2.fecha_stock < :fechaInicio
                              AND (:sucursalId = -1 OR sh2.sucursal_id = :sucursalId)
                            GROUP BY sh2.producto_id, sh2.sucursal_id
                        )
                        GROUP BY p.ambiente
                        """, nativeQuery = true)
        List<Object[]> findInitialStockByAmbiente(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("sucursalId") Long sucursalId);

        /**
         * Get initial stock snapshot before a specific date for familia drill-down
         * baseline
         */
        @Query(value = """
                        SELECT p.familia, SUM(sh.cantidad)
                        FROM stock_historico sh
                        JOIN productos p ON sh.producto_id = p.id
                        WHERE sh.id IN (
                            SELECT MAX(sh2.id)
                            FROM stock_historico sh2
                            WHERE sh2.fecha_stock < :fechaInicio
                              AND (:sucursalId = -1 OR sh2.sucursal_id = :sucursalId)
                            GROUP BY sh2.producto_id, sh2.sucursal_id
                        )
                        AND p.ambiente = :ambiente
                        GROUP BY p.familia
                        """, nativeQuery = true)
        List<Object[]> findInitialStockByFamilia(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("sucursalId") Long sucursalId,
                        @Param("ambiente") String ambiente);

        /**
         * Get daily net stock changes (deltas) for evolution calculation
         */
        @Query(value = """
                        SELECT p.ambiente,
                               sh.fecha_stock,
                               SUM(sh.cantidad - COALESCE(sh.diff_anterior, 0))
                        FROM stock_historico sh
                        JOIN productos p ON sh.producto_id = p.id
                        WHERE sh.fecha_stock BETWEEN :fechaInicio AND :fechaFin
                          AND (:sucursalId = -1 OR sh.sucursal_id = :sucursalId)
                        GROUP BY p.ambiente, sh.fecha_stock
                        ORDER BY sh.fecha_stock ASC
                        """, nativeQuery = true)
        List<Object[]> findDailyStockDeltasByAmbiente(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("fechaFin") LocalDate fechaFin,
                        @Param("sucursalId") Long sucursalId);

        /**
         * Get daily net stock changes (deltas) for familia drill-down evolution
         */
        @Query(value = """
                        SELECT p.ambiente,
                               p.familia,
                               sh.fecha_stock,
                               SUM(sh.cantidad - COALESCE(sh.diff_anterior, 0))
                        FROM stock_historico sh
                        JOIN productos p ON sh.producto_id = p.id
                        WHERE sh.fecha_stock BETWEEN :fechaInicio AND :fechaFin
                          AND (:sucursalId = -1 OR sh.sucursal_id = :sucursalId)
                          AND p.ambiente = :ambiente
                        GROUP BY p.ambiente, p.familia, sh.fecha_stock
                        ORDER BY sh.fecha_stock ASC
                        """, nativeQuery = true)
        List<Object[]> findDailyStockDeltasByFamilia(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("fechaFin") LocalDate fechaFin,
                        @Param("sucursalId") Long sucursalId,
                        @Param("ambiente") String ambiente);

        /**
         * Stock evolution by ambiente over time - DEPRECATED (Use deltas logic)
         */
        @Query(value = """
                        SELECT p.ambiente,
                               sh.fecha_stock,
                               SUM(sh.cantidad)
                        FROM stock_historico sh
                        JOIN productos p ON sh.producto_id = p.id
                        WHERE sh.id IN (
                            SELECT MAX(sh2.id)
                            FROM stock_historico sh2
                            WHERE sh2.fecha_stock BETWEEN :fechaInicio AND :fechaFin
                              AND (:sucursalId = -1 OR sh2.sucursal_id = :sucursalId)
                            GROUP BY sh2.producto_id, sh2.sucursal_id, sh2.fecha_stock
                        )
                        GROUP BY p.ambiente, sh.fecha_stock
                        ORDER BY sh.fecha_stock ASC, p.ambiente ASC
                        """, nativeQuery = true)
        List<Object[]> findStockEvolutionByAmbiente(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("fechaFin") LocalDate fechaFin,
                        @Param("sucursalId") Long sucursalId);

        /**
         * Stock evolution by familia (for drill-down) - DEPRECATED (Use deltas logic)
         */
        @Query(value = """
                        SELECT p.ambiente,
                               p.familia,
                               sh.fecha_stock,
                               SUM(sh.cantidad)
                        FROM stock_historico sh
                        JOIN productos p ON sh.producto_id = p.id
                        WHERE sh.id IN (
                            SELECT MAX(sh2.id)
                            FROM stock_historico sh2
                            WHERE sh2.fecha_stock BETWEEN :fechaInicio AND :fechaFin
                              AND (:sucursalId = -1 OR sh2.sucursal_id = :sucursalId)
                            GROUP BY sh2.producto_id, sh2.sucursal_id, sh2.fecha_stock
                        )
                        AND p.ambiente = :ambiente
                        GROUP BY p.ambiente, p.familia, sh.fecha_stock
                        ORDER BY sh.fecha_stock ASC, p.familia ASC
                        """, nativeQuery = true)
        List<Object[]> findStockEvolutionByFamilia(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("fechaFin") LocalDate fechaFin,
                        @Param("sucursalId") Long sucursalId,
                        @Param("ambiente") String ambiente);

        /**
         * Query 3: Stockout Analysis
         * Find products that reached zero quantity to identify breakage patterns
         */
        @Query(value = """
                        SELECT sh.*
                        FROM stock_historico sh
                        WHERE sh.cantidad = 0
                          AND sh.fecha_stock >= :fechaInicio
                          AND (:sucursalId = -1 OR sh.sucursal_id = :sucursalId)
                        ORDER BY sh.fecha_stock DESC
                        """, nativeQuery = true)
        List<StockHistorico> findStockouts(
                        @Param("fechaInicio") LocalDate fechaInicio,
                        @Param("sucursalId") Long sucursalId);

        @Query(value = """
                        SELECT sh.*
                        FROM stock_historico sh
                        JOIN productos p ON sh.producto_id = p.id
                        WHERE sh.nuevo_ingreso = true
                          AND p.sku IN (:skus)
                          AND (:sucursalId = -1 OR sh.sucursal_id = :sucursalId)
                        ORDER BY sh.id DESC
                        """, nativeQuery = true)
        List<StockHistorico> findAllIngresosForSkus(
                        @Param("skus") java.util.Collection<String> skus,
                        @Param("sucursalId") Long sucursalId);
}
