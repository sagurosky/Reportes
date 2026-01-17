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

    Optional<StockHistorico> findTopByProducto_SkuAndSucursalOrderByFechaStockDescIdDesc(
            String sku,
            Sucursal sucursal
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
