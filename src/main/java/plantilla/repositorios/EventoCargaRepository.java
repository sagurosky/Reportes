package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plantilla.dominio.EventoCarga;

import java.time.LocalDate;
import java.util.Optional;

public interface EventoCargaRepository extends JpaRepository<EventoCarga, Long> {

    boolean existsByNombreArchivo(String nombreArchivo);

    @Query("SELECT MAX(e.fechaArchivo) FROM EventoCarga e WHERE e.sucursal.id = :sucursalId")
    Optional<LocalDate> findMaxFechaArchivoBySucursalId(@Param("sucursalId") Long sucursalId);


}