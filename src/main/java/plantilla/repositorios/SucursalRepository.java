package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import plantilla.dominio.Sucursal;

import java.util.List;
import java.util.Optional;

@Repository
public interface SucursalRepository extends JpaRepository<Sucursal, Long> {

    Optional<Sucursal> findByCodDeposito(String codDeposito);

    Optional<Sucursal> findByIdDeposito(Long idDeposito);

    Optional<Sucursal> findByNombreIgnoreCase(String nombre);

    boolean existsByCodDeposito(String codDeposito);

    List<Sucursal> findByInhabilitadoFalse();
}
