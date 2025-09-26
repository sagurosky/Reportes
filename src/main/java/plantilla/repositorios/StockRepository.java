package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import plantilla.dominio.Stock;
import plantilla.dominio.Sucursal;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySucursal(Sucursal sucursal);

}
