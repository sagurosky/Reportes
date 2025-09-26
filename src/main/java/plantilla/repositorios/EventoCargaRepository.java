package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import plantilla.dominio.EventoCarga;

public interface EventoCargaRepository extends JpaRepository<EventoCarga, Long> {

    boolean existsByNombreArchivo(String nombreArchivo);
}