package plantilla.dominio;


import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "sucursales")
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String direccion;
    private String mail;
    private String provincia;
    private String pais;
    private Boolean inhabilitado;
}
