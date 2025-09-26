package plantilla.servicio;


import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plantilla.dominio.EventoCarga;
import plantilla.dominio.Producto;
import plantilla.dominio.Stock;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.EventoCargaRepository;
import plantilla.repositorios.ProductoRepository;
import plantilla.repositorios.StockRepository;
import plantilla.repositorios.SucursalRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class StockService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private EventoCargaRepository eventoCargaRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    private static final String SUCURSAL_DEFAULT = "Default";

    private Sucursal obtenerSucursalDefault() {
        return sucursalRepository.findByNombre(SUCURSAL_DEFAULT)
                .orElseGet(() -> {
                    Sucursal nueva = new Sucursal();
                    nueva.setNombre(SUCURSAL_DEFAULT);
                    nueva.setDireccion("N/A");
                    nueva.setMail("default@sistema.com");
                    nueva.setProvincia("N/A");
                    nueva.setPais("N/A");
                    nueva.setInhabilitado(Boolean.FALSE);
                    return sucursalRepository.save(nueva);
                });
    }

    @Transactional
    public void procesarRotacion(Sheet sheet, String nombreArchivo, Sucursal sucursal) {
        log.info("🚀 Iniciando procesamiento de rotación para archivo: {}", nombreArchivo, " sucursal: " + sucursal.getNombre());

        // 📌 Registrar evento
        EventoCarga evento = new EventoCarga();
        evento.setNombreArchivo(nombreArchivo);
        evento.setFecha(LocalDateTime.now());
        evento.setUsuario("sistema"); // TODO: usuario autenticado
        evento.setModulo("stock");
        eventoCargaRepository.save(evento);


        int procesados = 0;
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;

            try {
                String codigo = safeStringCell(row, 0);
                Integer acant = safeIntCell(row, 1);
                Integer acantmin = safeIntCell(row, 2);
                String descripcion = safeStringCell(row, 3);
                String fcolo = safeStringCell(row, 5);
                String talle = safeStringCell(row, 6);
                Integer fcant = safeIntCell(row, 7);
                String color = safeStringCell(row, 8);

                if (codigo == null || fcolo == null) continue;

                String codigoUnico = codigo + fcolo;

                // Guardar stock con sucursal
                Stock stock = new Stock();
                stock.setSucursal(sucursal);
                stock.setCantidadActual(acant);
                stock.setCantidadMin(acantmin);
                stock.setCantidadFisica(fcant);
                stock.setFechaCarga(LocalDateTime.now());
                stockRepository.save(stock);

                // Producto asociado
                Producto producto = productoRepository.findByCodigoUnicoAndStock(codigoUnico, stock)
                        .orElseGet(() -> {
                            Producto nuevo = new Producto();
                            nuevo.setCodigo(codigo);
                            nuevo.setDescripcion(descripcion);
                            nuevo.setColor(color);
                            nuevo.setColorCod(fcolo);
                            nuevo.setTalle(talle);
                            nuevo.setCodigoUnico(codigoUnico);
                            nuevo.setStock(stock);
                            return productoRepository.save(nuevo);
                        });

                procesados++;
            } catch (Exception ex) {
                log.error("❌ Error en fila {}: {}", row.getRowNum(), ex.getMessage());
            }
        }

        log.info("✅ Procesados {} registros para sucursal {}", procesados, sucursal.getNombre());
    }


    // 🔹 Helpers (los mismos que en tu ServicioImpl)
    private String safeStringCell(Row row, int index) {
        try {
            if (row.getCell(index) == null) return null;
            return switch (row.getCell(index).getCellType()) {
                case STRING -> row.getCell(index).getStringCellValue().trim();
                case NUMERIC -> String.valueOf((long) row.getCell(index).getNumericCellValue());
                case FORMULA -> row.getCell(index).getStringCellValue();
                default -> null;
            };
        } catch (Exception e) {
            log.warn("⚠️ Error leyendo celda String col {}: {}", index, e.getMessage());
            return null;
        }
    }

    private Integer safeIntCell(Row row, int index) {
        try {
            if (row.getCell(index) == null) return null;
            return switch (row.getCell(index).getCellType()) {
                case NUMERIC -> (int) row.getCell(index).getNumericCellValue();
                case STRING -> {
                    String val = row.getCell(index).getStringCellValue();
                    yield val.isBlank() ? null : Integer.parseInt(val.trim());
                }
                case FORMULA -> (int) row.getCell(index).getNumericCellValue();
                default -> null;
            };
        } catch (Exception e) {
            log.warn("⚠️ Error leyendo celda int col {}: {}", index, e.getMessage());
            return null;
        }
    }
}
