package plantilla.repositorios;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plantilla.dominio.EventoCarga;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventoCargaRepository extends JpaRepository<EventoCarga, Long> {

        boolean existsByNombreArchivo(String nombreArchivo);

        @Query("""
                        SELECT MAX(e.fechaArchivo)
                        FROM EventoCarga e
                        WHERE e.sucursal.id = :id
                        """)
        Optional<LocalDate> findMaxFechaArchivoBySucursalId(
                        @Param("id") Long idDeposito);

        boolean existsByEstado(String enProceso);

        /**
         * Query 3: Audit Report - Recent file loads
         * For audit dashboard showing upload compliance
         */
        @Query("""
                        SELECT ec
                        FROM EventoCarga ec
                        WHERE ec.fechaArchivo >= :fechaDesde
                        ORDER BY ec.fechaArchivo DESC, ec.fecha DESC
                        """)
        List<EventoCarga> findRecentLoads(@Param("fechaDesde") LocalDate fechaDesde);
}