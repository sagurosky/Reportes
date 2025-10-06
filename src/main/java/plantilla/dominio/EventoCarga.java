package plantilla.dominio;


import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "eventos_carga")
public class EventoCarga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreArchivo;
    private LocalDateTime fecha;
    private String usuario;
    private String modulo; // "stock", "pedidos", "caja", etc.
    private String estado;
    private String observaciones;
    private Long idStockInicial;
    private Long idStockFinal;
}