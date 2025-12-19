package plantilla.dominio;


import lombok.Data;

import javax.persistence.*;
@Data
@Entity
@Table(name = "sucursales")
public class Sucursal {

    @Id
    private Long idDeposito;   // viene del archivo (NO AUTO)

    @Column(unique = true)
    private String codDeposito;

    private String nombre;     // “Depósito” en el archivo

    private boolean inhabilitado = false;  // opcional
}
