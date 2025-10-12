package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plantilla.dominio.Stock;
import plantilla.dominio.Sucursal;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySucursal(Sucursal sucursal);

    @Query("""
                SELECT s
                FROM Stock s
                WHERE s.producto.codigoUnico = :codigoUnico
                  AND s.sucursal = :sucursal
                ORDER BY s.fechaStock DESC
            """)
    Optional<Stock> findUltimoStockPorProductoYSucursal(
            @Param("codigoUnico") String codigoUnico,
            @Param("sucursal") Sucursal sucursal);


}
