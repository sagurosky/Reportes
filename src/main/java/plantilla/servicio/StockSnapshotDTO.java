package plantilla.servicio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for stock snapshot grouped by ambiente and familia
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockSnapshotDTO {
    private String ambiente;
    private String familia;
    private String nivel3;
    private String nivel4;
    private Long cantidad;
}
