package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import plantilla.dominio.Sucursal;

import java.util.List;
import java.util.Optional;

@Repository
public interface SucursalRepository extends JpaRepository<Sucursal, Long> {

    Optional<Sucursal> findByNombre(String nombre);

    List<Sucursal> findByInhabilitadoFalse();

    boolean existsByNombre(String nombre);

    Optional<Sucursal> findByNombreIgnoreCase(String nombre);


}
