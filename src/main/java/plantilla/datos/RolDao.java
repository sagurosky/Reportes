package plantilla.datos;

import java.util.List;
import java.util.Optional;

import plantilla.dominio.Rol;
import plantilla.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface RolDao extends JpaRepository<Rol,Long>{


    Optional<Rol> findByNombre(String roleAdmin);
    boolean existsByNombre(String nombre);
}

