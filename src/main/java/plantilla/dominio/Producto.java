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

    @Column(unique = true, nullable = false)
    private String sku; // clave única global

    private String masterId;
    private String descripcion;
    private String color;

    private String ambiente;
    private String familia;
    private String nivel3;
    private String nivel4;
}
