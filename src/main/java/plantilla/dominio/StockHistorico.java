package plantilla.dominio;


import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "stock_historico")
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
    private LocalDate fechaStock;       // Fecha ingresada por el usuario
    private Integer cantidad;           // stock real del archivo

    private LocalDateTime fechaCarga;   // cuándo el usuario subió el archivo

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_carga_id", nullable = false)
    private EventoCarga eventoCarga;
}


