package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.ReserveStockUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.messaging.StockEventPublisherPort;
import ecommerce_event_driven.inventory.modules.product.domain.exceptions.InsufficientStockException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReserveStockService implements ReserveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockService.class);

    private final StockHoldTransaction stockHold;
    private final StockEventPublisherPort publisher;

    public ReserveStockService(StockHoldTransaction stockHold, StockEventPublisherPort publisher) {
        this.stockHold = stockHold;
        this.publisher = publisher;
    }

    /**
     * Publica so depois que hold retorna: ali o commit ja aconteceu e o lock das
     * linhas de product ja foi solto. Dentro da transacao, a vazao de um produto
     * passaria a depender do tempo de resposta do broker.
     */
    @Override
    public Result execute(Long idOrder, List<Item> items) {
        try {
            Result result = stockHold.hold(idOrder, items);

            if (result == Result.RESERVED) {
                publisher.publishStockReserved(idOrder);
            } else {
                log.debug("Pedido {} ja tinha reserva, evento repetido ignorado", idOrder);
            }
            return result;

        } catch (InsufficientStockException ex) {
            log.info("Reserva recusada: {}", ex.getMessage());
            publisher.publishStockRejected(ex.getIdOrder(), ex.getIdProduct());
            return Result.REJECTED;
        }
    }
}
