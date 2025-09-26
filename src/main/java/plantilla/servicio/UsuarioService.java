package plantilla.servicio;

import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.Query;
import plantilla.datos.UsuarioDao;
import plantilla.dominio.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service("userDetailsService") // Necesario para Spring Security
@Slf4j
public class UsuarioService implements UserDetailsService {

    @Autowired
    private UsuarioDao usuarioDao;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioDao.findByUsername(username);

        if (usuario == null) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        }

        // Convertimos el rol único en un GrantedAuthority
        GrantedAuthority authority = new SimpleGrantedAuthority(usuario.getRol().getNombre());

        boolean habilitado = !usuario.getInhabilitado(); // invertimos el flag si es 'inhabilitado'

        return new User(
                usuario.getUsername(),
                usuario.getPassword(),
                habilitado, // enabled
                true,       // accountNonExpired
                true,       // credentialsNonExpired
                true,       // accountNonLocked
                Collections.singletonList(authority)
        );
    }


    public void guardar(Usuario usuario) {
        if (usuario.getIdUsuario() != null) {
            usuario = usuarioDao.findById(usuario.getIdUsuario()).orElse(null);
        }
        usuarioDao.save(usuario);
    }

//    @Query("select * from usuario where inhabilitado='false'")
    public List<Usuario> listarUsuariosHabilitados() {
        return usuarioDao.findByInhabilitadoFalse();
    }
    public List<Usuario> listarUsuariosTodos() {
        return usuarioDao.findAll();
    }
}
