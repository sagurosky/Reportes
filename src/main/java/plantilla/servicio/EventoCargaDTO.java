package plantilla.servicio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for event listing in Stock Audit Dashboard
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoCargaDTO {
    private LocalDateTime fecha;
    private String sucursalNombre;
    private String usuario;
    private String nombreArchivo;
    private Integer procesados;
    private Integer totalRegistros;
    private Integer porcentaje;
    private String estado;
}
