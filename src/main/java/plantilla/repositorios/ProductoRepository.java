package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plantilla.dominio.Producto;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("SELECT p.sku FROM Producto p WHERE p.sku LIKE :prefix% ORDER BY p.sku ASC")
    List<String> findSkusByPrefix(@Param("prefix") String prefix, org.springframework.data.domain.Pageable pageable);
}