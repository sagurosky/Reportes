package plantilla.dominio;


import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer cantidadActual;   // acant
    private Integer cantidadMin;      // acantmin
    private Integer cantidadFisica;   // fcant

    private LocalDateTime fechaCarga;

    // 🔗 Relación con Sucursal
    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = false)
    private Sucursal sucursal;

    // 👇 Relación bidireccional (opcional, pero recomendable)
    @OneToMany(mappedBy = "stock", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Producto> productos = new ArrayList<>();
}


