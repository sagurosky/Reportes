package plantilla.dominio;


import lombok.Data;

import javax.persistence.*;
@Data
@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;        // artcod
    private String descripcion;   // artdes
    private String color;         // fcotxt (texto)
    private String colorCod;      // fcolo (abreviado)
    private String talle;         // ftall

    // 👇 importante: ya no lleva guion
    @Column(unique = true, nullable = false)
    private String codigoUnico;   // codigo + colorCod

    // 🔗 Relación con Stock
    @ManyToOne
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;
}
