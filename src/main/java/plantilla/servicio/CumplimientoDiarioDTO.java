package plantilla.servicio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for daily compliance tracking in Stock Audit Dashboard
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CumplimientoDiarioDTO {
    private LocalDate fecha;
    private String sucursalNombre;
    private String diaEsperado; // Day of week name (e.g., "Lunes")
    private Integer cantidadCargas;
    private Boolean estadoVolumen; // true if cargas > 2
    private Boolean cumpleRegla; // compliance with business rule
    private String updStockDia; // Expected update day from sucursales.upd_stock
}
