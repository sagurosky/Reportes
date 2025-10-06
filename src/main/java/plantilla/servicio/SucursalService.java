package plantilla.servicio;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.SucursalRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SucursalService {

    private final SucursalRepository sucursalRepository;

    public List<Sucursal> listarActivas() {
        return sucursalRepository.findByInhabilitadoFalse();
    }


    public List<Sucursal> listarTodas() {
        return sucursalRepository.findAll();
    }

    public boolean existePorNombre(String nombre) {
        return sucursalRepository.existsByNombre(nombre);
    }

    public Sucursal buscarPorNombre(String nombre) {
        return sucursalRepository.findByNombreIgnoreCase(nombre).orElse(null);
    }

    public Sucursal guardar(Sucursal sucursal) {
        if (sucursal.getInhabilitado() == null) {
            sucursal.setInhabilitado(false);
        }
        return sucursalRepository.save(sucursal);
    }

    public void inhabilitar(Long id) {
        sucursalRepository.findById(id).ifPresent(s -> {
            s.setInhabilitado(true);
            sucursalRepository.save(s);
        });
    }

    public void activar(Long id) {
        sucursalRepository.findById(id).ifPresent(s -> {
            s.setInhabilitado(false);
            sucursalRepository.save(s);
        });
    }

    public Sucursal buscarPorId(Long id) {
        return sucursalRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Sucursal no encontrada con id " + id));
    }
}
