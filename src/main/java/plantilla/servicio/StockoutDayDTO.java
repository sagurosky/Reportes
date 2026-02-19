package plantilla.servicio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for stockout analysis grouped by day of week
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockoutDayDTO {
    private DayOfWeek dayOfWeek;
    private String diaNombre; // Spanish day name for display
    private int productoCount;
    private List<ProductoStockoutInfo> productos;

    public StockoutDayDTO(DayOfWeek dayOfWeek, String diaNombre) {
        this.dayOfWeek = dayOfWeek;
        this.diaNombre = diaNombre;
        this.productos = new ArrayList<>();
        this.productoCount = 0;
    }

    /**
     * Nested class for product details in stockout
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductoStockoutInfo {
        private String sku;
        private String descripcion;
        private String ambiente;
        private String familia;
        private String sucursal;
        private LocalDate fechaStock;
        private LocalDate fechaUltimoIngreso;
        private Integer cantidadUltimoIngreso; // Total stock at last entry
        private Integer agregado; // cantidad afgregada con el ultimo ingreso
    }
}
