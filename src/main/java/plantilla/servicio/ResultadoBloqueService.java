package plantilla.servicio;

import org.apache.poi.ss.usermodel.Row;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plantilla.dominio.EventoCarga;
import plantilla.dominio.Producto;
import plantilla.dominio.StockHistorico;
import plantilla.dominio.Sucursal;
import plantilla.repositorios.ProductoRepository;
import plantilla.repositorios.StockHistoricoRepository;
import plantilla.repositorios.SucursalRepository;
import plantilla.util.TiempoUtils;
import plantilla.util.Validadores;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static plantilla.servicio.StockService.*;
import static plantilla.util.Validadores.safeStringCell;
@Service

public class ResultadoBloqueService {

    @Autowired
    ProductoRepository productoRepository;
    @Autowired
    StockHistoricoRepository stockHistoricoRepository;
    @Autowired
    SucursalRepository sucursalRepository;


    @PersistenceContext
    private EntityManager entityManager;


    @Transactional
    public ResultadoBloqueDTO procesarBloque(
            List<Row> rows,
            Sucursal sucursal,
            LocalDate fechaStock,
            EventoCarga evento) {

        EventoCarga eventoManaged = entityManager.merge(evento);

        Long stockInicial = null;
        Long stockFinal = null;

        int i = 0;

        for (Row row : rows) {

            String sku = safeStringCell(row, IDX_SKU);
            if (sku == null || sku.isBlank()) continue;

            Integer cantidad = Validadores.safeIntCell(row, IDX_CANTIDAD);

            // -----------------------
            // Producto
            // -----------------------
            Producto producto = productoRepository.findBySku(sku)
                    .orElseGet(() -> {
                        Producto nuevo = new Producto();
                        nuevo.setSku(sku);
                        nuevo.setMasterId(safeStringCell(row, IDX_MASTER_ID));
                        nuevo.setDescripcion(safeStringCell(row, IDX_DESCRIPCION));
                        nuevo.setColor(safeStringCell(row, IDX_COLOR));

                        nuevo.setAmbiente(safeStringCell(row, IDX_AMBIENTE));
                        nuevo.setFamilia(safeStringCell(row, IDX_FAMILIA));
                        nuevo.setNivel3(safeStringCell(row, IDX_NIVEL3));
                        nuevo.setNivel4(safeStringCell(row, IDX_NIVEL4));
                        return productoRepository.save(nuevo);
                    });

            // -----------------------
            // Último stock
            // -----------------------
            Optional<StockHistorico> ultimoOpt =
                    stockHistoricoRepository.findTopByProducto_SkuAndSucursalOrderByFechaStockDescIdDesc(
                            sku, sucursal
                    );

            boolean hayCambio = ultimoOpt
                    .map(u -> !Objects.equals(u.getCantidad(), cantidad))
                    .orElse(true);

            if (!hayCambio) continue;

            StockHistorico stock = new StockHistorico();
            stock.setProducto(producto);
            stock.setSucursal(sucursal);
            stock.setCantidad(cantidad);
            stock.setFechaStock(fechaStock);
            stock.setFechaCarga(TiempoUtils.ahora());
            stock.setEventoCarga(eventoManaged);

            entityManager.persist(stock);

            if (stockInicial == null) {
                entityManager.flush();
                stockInicial = stock.getId();
            }
            stockFinal = stock.getId();

            if (++i % 50 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        entityManager.flush();
        entityManager.clear();

        return new ResultadoBloqueDTO(stockInicial, stockFinal);
    }

}
