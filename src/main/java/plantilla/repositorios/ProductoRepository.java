package plantilla.repositorios;


import org.springframework.data.jpa.repository.JpaRepository;
import plantilla.dominio.Producto;
import plantilla.dominio.Stock;
import plantilla.dominio.Sucursal;

import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Optional<Producto> findByCodigoAndColorAndTalle(String codigo, String color, String talle);

    Optional<Producto> findByCodigoUnicoAndStock(String codigoUnico, Stock stock);



}