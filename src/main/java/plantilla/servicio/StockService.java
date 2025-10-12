package plantilla.servicio;


import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import plantilla.dominio.EventoCarga;
import plantilla.dominio.Producto;
import plantilla.dominio.Stock;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.EventoCargaRepository;
import plantilla.repositorios.ProductoRepository;
import plantilla.repositorios.StockRepository;
import plantilla.repositorios.SucursalRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import plantilla.util.TiempoUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
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

    //    @Transactional
    public void procesarRotacion(Sheet sheet, String nombreArchivo, Sucursal sucursal, LocalDate fechaStock) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuarioActual = auth != null ? auth.getName() : "sistema";
        log.info("🚀 Iniciando procesamiento de rotación para archivo: {} sucursal: {}", nombreArchivo, sucursal.getNombre());

        // 📌 Registrar evento
        EventoCarga evento = new EventoCarga();
        evento.setNombreArchivo(nombreArchivo);
        evento.setSucursal(sucursal);
        evento.setFecha(TiempoUtils.ahora());
        evento.setFechaArchivo(fechaStock);
        evento.setUsuario(usuarioActual);
        evento.setModulo("stock");
        evento.setEstado("iniciado");
        eventoCargaRepository.save(evento);

        int procesados = 0;
        Long stockInicial = null;
        Long stockFinal = null;

        try {
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                // Si encontramos un código ficticio, tiramos error
                if ("FORZAR_ERROR".equalsIgnoreCase(safeStringCell(row, 0))) {
                    throw new IllegalStateException("Código inválido detectado en fila " + row.getRowNum());
                }

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

                // 🔍 Buscar producto SOLO por codigoUnico
                Producto producto = productoRepository.findByCodigoUnico(codigoUnico)
                        .orElseGet(() -> {
                            Producto nuevo = new Producto();
                            nuevo.setCodigo(codigo);
                            nuevo.setDescripcion(descripcion);
                            nuevo.setColor(color);
                            nuevo.setColorCod(fcolo);
                            nuevo.setTalle(talle);
                            nuevo.setCodigoUnico(codigoUnico);
                            return productoRepository.save(nuevo);
                        });

                // 🔍 Buscar último registro existente
                Optional<Stock> ultimoOpt = stockRepository.findUltimoStockPorProductoYSucursal(codigoUnico, sucursal);

                boolean debeGuardar = true;

                if (ultimoOpt.isPresent()) {
                    Stock ultimo = ultimoOpt.get();
                    debeGuardar = !Objects.equals(ultimo.getCantidadActual(), acant)
                            || !Objects.equals(ultimo.getCantidadMin(), acantmin)
                            || !Objects.equals(ultimo.getCantidadFisica(), fcant);
                }


                // 📌 Crear nuevo registro de stock
                // DMS lo guarda solo si es distinto acant, fcant o cantidad minima respecto del ultimo registro (segun fecha)
                if (debeGuardar) {
                    Stock stock = new Stock();
                    stock.setProducto(producto);
                    stock.setSucursal(sucursal);
                    stock.setCantidadActual(acant);
                    stock.setCantidadMin(acantmin);
                    stock.setCantidadFisica(fcant);
                    stock.setFechaCarga(TiempoUtils.ahora());
                    stock.setFechaStock(fechaStock);
                    stock = stockRepository.save(stock);

                    if (stockInicial == null) {
                        stockInicial = stock.getId();
                    }
                    stockFinal = stock.getId();
                    procesados++;

                }

            }
            log.info("✅ Procesados {} registros nuevos (omitidos {} sin cambios) para sucursal {}",
                    procesados, (sheet.getLastRowNum() - procesados), sucursal.getNombre());
            
            evento.setEstado("completado");
            evento.setIdStockInicial(stockInicial);
            evento.setIdStockFinal(stockFinal);
            eventoCargaRepository.save(evento);
        } catch (Exception ex) {
            evento.setEstado("fallido");
            evento.setIdStockInicial(stockInicial);
            evento.setIdStockFinal(stockFinal);
            evento.setObservaciones(ex.getMessage());
            eventoCargaRepository.save(evento);
            log.error("❌ Error en id {}: {}", stockFinal, ex.getMessage());
            throw ex;

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
