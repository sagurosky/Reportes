package plantilla.datos;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import plantilla.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.validation.constraints.NotEmpty;
import java.util.List;

//para poder hacer extends de una interface, la clase debe ser una interfase tambien
public interface UsuarioDao extends JpaRepository<Usuario,Long>{
    //podria haber extendido de CrudRepository, pero JpaRepository tiene otras funciones
Usuario findByUsername(String username);
        //este metodo tiene que estar escrito tal cual porque es parte de la configuracion de SpringSecurity
//Spring lohace todo en automatico


    List<Usuario> findByInhabilitadoFalse();

    @Query("SELECT u FROM Usuario u WHERE u.inhabilitado = false AND u.username = ?1")
    Usuario traerUsuarioHabilitadoPorNombre(String username);
}

