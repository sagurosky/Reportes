package plantilla.dominio;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "stock_historico", indexes = {
        @Index(name = "idx_stock_fecha", columnList = "fechaStock"),
        @Index(name = "idx_stock_producto", columnList = "producto_id"),
        @Index(name = "idx_stock_sucursal", columnList = "sucursal_id"),
        @Index(name = "idx_stock_composite", columnList = "fechaStock, sucursal_id, producto_id")
})
public class StockHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Claves foráneas
    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    // Datos del archivo
    private LocalDate fechaStock; // Fecha ingresada por el usuario
    private Integer cantidad; // stock real del archivo

    private LocalDateTime fechaCarga; // cuándo el usuario subió el archivo

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_carga_id", nullable = false)
    private EventoCarga eventoCarga;

    private Boolean esInicial;
    private Integer diffAnterior;
    private Boolean nuevoIngreso;
}
