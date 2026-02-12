package plantilla.servicio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for consumption vs stock comparison
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsumoStockDTO {
    private LocalDate fecha;
    private Long consumo; // ABS(diff_anterior)
    private Long stockActual;
}
