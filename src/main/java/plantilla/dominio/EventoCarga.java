package plantilla.dominio;


import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "eventos_carga")
public class EventoCarga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreArchivo;
    private LocalDateTime fecha;       // fecha del evento
    private LocalDate fechaArchivo;    // fechaStock ingresada
    private String usuario;
    private String estado;
    private String observaciones;
    private String modulo; // "stock", "pedidos", "caja", etc.

    private Long idStockInicial;       // primer ID generado
    private Long idStockFinal;         // último ID generado

    @ManyToOne
    @JoinColumn(name = "sucursal_id")
    private Sucursal sucursal;

    @OneToMany(mappedBy = "eventoCarga")
    private List<StockHistorico> stocks;
}