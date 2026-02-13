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

  // ===== AUDIT DASHBOARD QUERIES =====

  /**
   * Get total count of stock loads for audit KPIs
   */
  @Query("""
      SELECT COUNT(ec)
      FROM EventoCarga ec
      WHERE UPPER(ec.modulo) LIKE 'STOCK%'
        AND ec.fechaArchivo BETWEEN :fechaInicio AND :fechaFin
        AND (:sucursalId IS NULL OR ec.sucursal.id = :sucursalId)
      """)
  Long countTotalCargas(
      @Param("fechaInicio") LocalDate fechaInicio,
      @Param("fechaFin") LocalDate fechaFin,
      @Param("sucursalId") Long sucursalId);

  /**
   * Get count of successful stock loads for audit KPIs
   */
  @Query("""
      SELECT COUNT(ec)
      FROM EventoCarga ec
      WHERE UPPER(ec.modulo) LIKE 'STOCK%'
        AND ec.estado = 'COMPLETADO'
        AND ec.fechaArchivo BETWEEN :fechaInicio AND :fechaFin
        AND (:sucursalId IS NULL OR ec.sucursal.id = :sucursalId)
      """)
  Long countCargasExitosas(
      @Param("fechaInicio") LocalDate fechaInicio,
      @Param("fechaFin") LocalDate fechaFin,
      @Param("sucursalId") Long sucursalId);

  /**
   * Get average completion percentage for audit KPIs
   */
  @Query("""
      SELECT AVG(ec.porcentaje)
      FROM EventoCarga ec
      WHERE UPPER(ec.modulo) LIKE 'STOCK%'
        AND ec.fechaArchivo BETWEEN :fechaInicio AND :fechaFin
        AND (:sucursalId IS NULL OR ec.sucursal.id = :sucursalId)
      """)
  Double getPromedioCompletado(
      @Param("fechaInicio") LocalDate fechaInicio,
      @Param("fechaFin") LocalDate fechaFin,
      @Param("sucursalId") Long sucursalId);

  /**
   * Get daily compliance data grouped by date and sucursal (JPQL version for
   * cross-DB)
   */
  @Query("""
      SELECT ec.fechaArchivo, ec.sucursal.nombre, ec.sucursal.updStock, COUNT(ec)
      FROM EventoCarga ec
      WHERE UPPER(ec.modulo) LIKE 'STOCK%'
        AND ec.fechaArchivo BETWEEN :fechaInicio AND :fechaFin
        AND (:sucursalId IS NULL OR ec.sucursal.id = :sucursalId)
      GROUP BY ec.fechaArchivo, ec.sucursal.nombre, ec.sucursal.updStock
      ORDER BY ec.fechaArchivo DESC
      """)
  List<Object[]> findCumplimientoDiario(
      @Param("fechaInicio") LocalDate fechaInicio,
      @Param("fechaFin") LocalDate fechaFin,
      @Param("sucursalId") Long sucursalId);

  /**
   * Get recent event loads for audit table
   * Returns: [fecha, sucursal_nombre, usuario, nombre_archivo, procesados,
   * total_registros, porcentaje, estado]
   */
  @Query("""
      SELECT
          ec.fecha,
          ec.sucursal.nombre,
          ec.usuario,
          ec.nombreArchivo,
          ec.procesados,
          ec.totalRegistros,
          ec.porcentaje,
          ec.estado
      FROM EventoCarga ec
      WHERE UPPER(ec.modulo) LIKE 'STOCK%'
        AND ec.fechaArchivo BETWEEN :fechaInicio AND :fechaFin
        AND (:sucursalId IS NULL OR ec.sucursal.id = :sucursalId)
      ORDER BY ec.fecha DESC
      """)
  List<Object[]> findEventosCarga(
      @Param("fechaInicio") LocalDate fechaInicio,
      @Param("fechaFin") LocalDate fechaFin,
      @Param("sucursalId") Long sucursalId);
}