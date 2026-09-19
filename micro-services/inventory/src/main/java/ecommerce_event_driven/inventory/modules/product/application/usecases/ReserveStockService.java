package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.ReserveStockUseCase;
import ecommerce_event_driven.inventory.modules.product.domain.exceptions.InsufficientStockException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReserveStockService implements ReserveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockService.class);

    private final StockHoldTransaction stockHold;
    private final StockRejectionTransaction stockRejection;

    public ReserveStockService(StockHoldTransaction stockHold, StockRejectionTransaction stockRejection) {
        this.stockHold = stockHold;
        this.stockRejection = stockRejection;
    }

    /**
     * Com outbox: a publicacao de stock.reserved entra na mesma transacao da
     * reserva (dentro de StockHoldTransaction). A publicacao de stock.rejected
     * vai numa transacao propria (StockRejectionTransaction) apos o rollback.
     */
    @Override
    public Result execute(Long idOrder, List<Item> items) {
        try {
            Result result = stockHold.hold(idOrder, items);

            if (result == Result.RESERVED) {
                // Publicacao ja foi feita dentro de hold()
                return Result.RESERVED;
            } else {
                log.debug("Pedido {} ja tinha reserva, evento repetido ignorado", idOrder);
                return Result.ALREADY_RESERVED;
            }

        } catch (InsufficientStockException ex) {
            // A transacao da reserva ja fez rollback. A recusa vai numa transacao
            // propria: se o processo cair entre as duas, a reentrega reavalia do zero.
            log.info("Reserva recusada: {}", ex.getMessage());
            stockRejection.record(ex.getIdOrder(), ex.getIdProduct());
            return Result.REJECTED;
        }
    }
}
