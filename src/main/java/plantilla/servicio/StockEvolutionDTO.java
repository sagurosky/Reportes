package plantilla.servicio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for stock evolution over time by categoria
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockEvolutionDTO {
    private String categoria; // ambiente or familia depending on drill-down level
    private LocalDate fecha;
    private Long cantidad;
}
