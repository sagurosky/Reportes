package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import plantilla.dominio.Producto;

import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findBySku(String sku);

    boolean existsBySku(String sku);
}