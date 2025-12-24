package plantilla.servicio;


import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plantilla.dominio.EventoCarga;
import plantilla.dominio.Producto;

import plantilla.dominio.StockHistorico;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.EventoCargaRepository;
import plantilla.repositorios.ProductoRepository;
import plantilla.repositorios.StockHistoricoRepository;
import plantilla.repositorios.SucursalRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import plantilla.util.TiempoUtils;
import plantilla.util.Validadores;


import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static plantilla.util.Validadores.safeStringCell;

@Service
@Slf4j
public class StockService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private StockHistoricoRepository stockHistoricoRepository;

    @Autowired
    private EventoCargaRepository eventoCargaRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private ResultadoBloqueService resultadoBloqueService;

    private static final String SUCURSAL_DEFAULT = "Default";

    public static final int IDX_DEPOSITO=0;
    public static final int IDX_COD_DEPOSITO=1;
    public static final int IDX_ID_DEPOSITO=2;
    public static final int IDX_MASTER_ID=3;
    public static final int IDX_SKU=4;
    public static final int IDX_COLOR=5;
    public static final int IDX_DESCRIPCION=6;
    public static final int IDX_AMBIENTE=7;
    public static final int IDX_FAMILIA=8;
    public static final int IDX_NIVEL3=9;
    public static final int IDX_NIVEL4=10;
    public static final int IDX_CANTIDAD=11;

    public static final int BLOQUE_SIZE = 500;




    //    @Transactional
    public void procesarStock(
            Sheet sheet,
            String nombreArchivo,
            LocalDate fechaStock,
            EventoCarga evento) {

        int totalRegistros = sheet.getPhysicalNumberOfRows() - 1;


        evento.setTotalRegistros(totalRegistros);
        evento.setProcesados(0);
        evento.setPorcentaje(0);
        evento.setEstado("EN_PROCESO");

        evento = eventoCargaRepository.save(evento);


        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuarioActual = auth != null ? auth.getName() : "sistema";

        // ==========================
        // Resolver sucursal
        // ==========================
        Row firstDataRow = obtenerPrimeraFilaDatos(sheet);

        Long idDeposito = Validadores.safeLongCell(firstDataRow, IDX_ID_DEPOSITO);
        String codDeposito = Validadores.safeStringCell(firstDataRow, IDX_COD_DEPOSITO);
        String nombreDeposito = Validadores.safeStringCell(firstDataRow, IDX_DEPOSITO);

        Sucursal sucursal = resolverSucursal(
                idDeposito, codDeposito, nombreDeposito, nombreArchivo
        );


        Long stockInicial = null;
        Long stockFinal = null;

        // ==========================
        // Procesamiento por bloques
        // ==========================
        List<Row> bloque = new ArrayList<>(BLOQUE_SIZE);

        try {
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                bloque.add(row);

                if (bloque.size() == BLOQUE_SIZE) {
                    ResultadoBloqueDTO r = resultadoBloqueService.procesarBloque(
                            bloque, sucursal, fechaStock, evento
                    );


                    int procesadosHastaAhora = evento.getProcesados() + bloque.size();
                    evento.setProcesados(procesadosHastaAhora);

                    int porcentaje = (int)
                            ((procesadosHastaAhora * 100.0) / evento.getTotalRegistros());

                    evento.setPorcentaje(Math.min(porcentaje, 100));

                    eventoCargaRepository.save(evento);





                    if (r.getStockInicial() != null && stockInicial == null) {

                        stockInicial = r.getStockInicial();
                    }

                    if (r.getStockFinal() != null) {
                        stockFinal = r.getStockFinal();
                    }

                    bloque.clear();

                }
            }

            // último bloque incompleto
            if (!bloque.isEmpty()) {
                ResultadoBloqueDTO r = resultadoBloqueService.procesarBloque(
                        bloque, sucursal, fechaStock, evento
                );

                if (r.getStockInicial() != null && stockInicial == null) {
                    stockInicial = r.getStockInicial();
                }

                if (r.getStockFinal() != null) {
                    stockFinal = r.getStockFinal();
                }
            }

            evento.setEstado("COMPLETADO");
            evento.setIdStockInicial(stockInicial);
            evento.setIdStockFinal(stockFinal);
            EventoCarga e=eventoCargaRepository.save(evento);
            log.info("stock inicial:  "+e.getIdStockInicial());

            log.info("stock inicial : "+e.getIdStockFinal());
            log.info("stock id : "+e.getId());
        } catch (Exception ex) {
            evento.setEstado("FALLIDO");
            evento.setIdStockInicial(stockInicial);
            evento.setIdStockFinal(stockFinal);
            evento.setObservaciones(ex.getMessage());
            eventoCargaRepository.save(evento);
            throw ex;
        }
    }

    public Sucursal resolverSucursalDesdeArchivo(
            Sheet sheet,
            String nombreArchivo
    ) {
        Row firstDataRow = obtenerPrimeraFilaDatos(sheet);

        Long idDeposito = Validadores.safeLongCell(firstDataRow, IDX_ID_DEPOSITO);
        String codDeposito = Validadores.safeStringCell(firstDataRow, IDX_COD_DEPOSITO);
        String nombreDeposito = Validadores.safeStringCell(firstDataRow, IDX_DEPOSITO);

        return resolverSucursal(
                idDeposito,
                codDeposito,
                nombreDeposito,
                nombreArchivo
        );
    }


    private Row obtenerPrimeraFilaDatos(Sheet sheet) {
        for (Row row : sheet) {
            if (row.getRowNum() > 0) {
                return row;
            }
        }
        throw new IllegalStateException("El archivo no contiene filas de datos");
    }

    private Sucursal resolverSucursal(
            Long idDeposito,
            String codDeposito,
            String nombreDeposito,
            String nombreArchivo) {

        return sucursalRepository.findByIdDeposito(idDeposito)
                .orElseGet(() -> {
                    Sucursal nueva = new Sucursal();
                    nueva.setIdDeposito(idDeposito);
                    nueva.setCodDeposito(codDeposito);
                    nueva.setNombre(
                            nombreDeposito != null
                                    ? nombreDeposito
                                    : extraerSucursalDesdeNombreArchivo(nombreArchivo)
                    );
                    nueva.setInhabilitado(false);
                    return sucursalRepository.save(nueva);
                });
    }


    private String extraerSucursalDesdeNombreArchivo(String nombreArchivo) {
        String sinExtension = nombreArchivo.replaceFirst("[.][^.]+$", "");
        int idx = sinExtension.lastIndexOf("_");
        return idx != -1
                ? sinExtension.substring(idx + 1).trim()
                : sinExtension;
    }
}
