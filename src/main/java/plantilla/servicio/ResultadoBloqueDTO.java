package plantilla.servicio;

public class ResultadoBloqueDTO {


        private final Long stockInicial;
        private final Long stockFinal;

        public ResultadoBloqueDTO(Long stockInicial, Long stockFinal) {
            this.stockInicial = stockInicial;
            this.stockFinal = stockFinal;
        }

        public Long getStockInicial() {
            return stockInicial;
        }

        public Long getStockFinal() {
            return stockFinal;
        }
    }

