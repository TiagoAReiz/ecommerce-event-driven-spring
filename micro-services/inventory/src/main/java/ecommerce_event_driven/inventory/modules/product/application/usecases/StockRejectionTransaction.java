package ecommerce_event_driven.inventory.modules.product.application.usecases;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.messaging.StockEventPublisherPort;

/**
 * Transacao propria para gravar stock.rejected apos falha de reserva.
 *
 * <p>Chamada APOS rollback da transacao de reserva (fora dela). Se o processo
 * cair entre as duas transacoes, a reentrega do order.created reavalia do zero.
 * Por isso pode ser uma transacao propria com propagacao REQUIRES_NEW.
 */
@Component
public class StockRejectionTransaction {

    private final StockEventPublisherPort publisher;

    public StockRejectionTransaction(StockEventPublisherPort publisher) {
        this.publisher = publisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long idOrder, Long idProduct) {
        publisher.publishStockRejected(idOrder, idProduct);
    }
}
