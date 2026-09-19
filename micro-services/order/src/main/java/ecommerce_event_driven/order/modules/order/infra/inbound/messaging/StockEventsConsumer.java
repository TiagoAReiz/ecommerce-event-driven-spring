package ecommerce_event_driven.order.modules.order.infra.inbound.messaging;

import ecommerce_event_driven.order.modules.order.application.ports.inbound.messaging.StockEventsConsumerPort;
import ecommerce_event_driven.order.modules.order.application.ports.inbound.usecases.CancelOrderUseCase;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockRejectedEvent;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StockEventsConsumer implements StockEventsConsumerPort {

    private static final Logger log = LoggerFactory.getLogger(StockEventsConsumer.class);

    private final CancelOrderUseCase cancelOrder;

    public StockEventsConsumer(CancelOrderUseCase cancelOrder) {
        this.cancelOrder = cancelOrder;
    }

    /**
     * Estoque garantido: o pedido segue em pending esperando o pagamento. Nao
     * ha transicao de status para este evento no modelo atual.
     */
    @Override
    @KafkaListener(topics = "ecommerce.stock.reserved.v1", groupId = "order")
    public void onStockReserved(StockReservedEvent event) {
        log.info("Estoque reservado para o pedido {}", event.orderId());
    }

    @Override
    @KafkaListener(topics = "ecommerce.stock.rejected.v1", groupId = "order")
    public void onStockRejected(StockRejectedEvent event) {
        cancelOrder.execute(event.orderId(), "estoque indisponivel para o produto " + event.productId());
    }
}
