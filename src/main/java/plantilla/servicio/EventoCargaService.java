package plantilla.servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import plantilla.dominio.EventoCarga;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.EventoCargaRepository;
import plantilla.util.TiempoUtils;

import java.time.LocalDate;
@Service
public class EventoCargaService {
    @Autowired
    private EventoCargaRepository eventoCargaRepository;

    public EventoCarga buscarPorId(Long id) {
        return eventoCargaRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "EventoCarga no encontrado: " + id
                ));
    }

    public void marcarCompletado(EventoCarga evento) {
        evento.setEstado("COMPLETADO");
        eventoCargaRepository.save(evento);
    }

    public void marcarFallido(Long eventoId, String error) {
        EventoCarga evento = buscarPorId(eventoId);
        evento.setEstado("FALLIDO");
        evento.setObservaciones(error);
        eventoCargaRepository.save(evento);
    }
    public boolean existeArchivo(String nombreArchivo) {
        return eventoCargaRepository.existsByNombreArchivo(nombreArchivo);
    }

    public EventoCarga crearEventoInicial(
            String nombreArchivo,
            Sucursal sucursal,
            LocalDate fechaStock,
            String usuario,
            int totalRegistros) {

        EventoCarga evento = new EventoCarga();
        evento.setNombreArchivo(nombreArchivo);
        evento.setSucursal(sucursal);
        evento.setFecha(TiempoUtils.ahora());
        evento.setFechaArchivo(fechaStock);
        evento.setModulo("Stock");
        evento.setUsuario(usuario);

        evento.setEstado("EN_PROCESO");
        evento.setTotalRegistros(totalRegistros);
        evento.setProcesados(0);
        evento.setPorcentaje(0);

        return eventoCargaRepository.save(evento);
    }
}
