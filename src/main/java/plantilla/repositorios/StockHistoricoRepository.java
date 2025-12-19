package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plantilla.dominio.Producto;
import plantilla.dominio.StockHistorico;
import plantilla.dominio.StockHistorico;
import plantilla.dominio.Sucursal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockHistoricoRepository extends JpaRepository<StockHistorico, Long> {

    @Query("""
            SELECT s
            FROM StockHistorico s
            WHERE s.producto.sku = :sku
              AND s.sucursal = :sucursal
            ORDER BY s.fechaStock DESC
           """)
    Optional<StockHistorico> findUltimoStockPorSkuYSucursal(
            @Param("sku") String sku,
            @Param("sucursal") Sucursal sucursal
    );

    List<StockHistorico> findByProductoAndSucursalOrderByFechaStockAsc(
            Producto producto,
            Sucursal sucursal
    );

    boolean existsByProductoAndSucursalAndFechaStock(
            Producto producto,
            Sucursal sucursal,
            LocalDate fechaStock
    );
}
