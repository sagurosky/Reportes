package plantilla.dominio;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(
        name = "sucursales",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "id_deposito")
        }
)
public class Sucursal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;   // ID interno del sistema

    @Column(name = "id_deposito", nullable = false)
    private Long idDeposito;

    @Column(name = "cod_deposito", nullable = false)
    private String codDeposito;

    private String nombre;

    private String updStock;

    private boolean inhabilitado = false;
}
