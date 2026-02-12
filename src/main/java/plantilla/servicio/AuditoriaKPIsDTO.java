package plantilla.servicio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Stock Audit Dashboard KPIs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaKPIsDTO {
    private Long totalCargas;
    private Long cargasExitosas;
    private Long conErrores;
    private Double promedioCompletado;
}
