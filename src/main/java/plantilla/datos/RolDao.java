package plantilla.datos;

import java.util.Optional;

import plantilla.dominio.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolDao extends JpaRepository<Rol, Long> {

    Optional<Rol> findByNombre(String roleAdmin);

    boolean existsByNombre(String nombre);
}
