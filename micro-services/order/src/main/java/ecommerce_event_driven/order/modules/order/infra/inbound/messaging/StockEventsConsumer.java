package ecommerce_event_driven.order.modules.order.infra.inbound.messaging;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.order.application.ports.inbound.messaging.StockEventsConsumerPort;
import ecommerce_event_driven.order.modules.order.application.ports.inbound.usecases.CancelOrderUseCase;
import ecommerce_event_driven.order.modules.order.application.usecases.StockCommitFailedEventHandler;
import ecommerce_event_driven.order.modules.order.application.usecases.StockCommittedEventHandler;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockCommitFailedEvent;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockCommittedEvent;
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
    private final StockCommittedEventHandler stockCommittedHandler;
    private final StockCommitFailedEventHandler stockCommitFailedHandler;

    public StockEventsConsumer(
            CancelOrderUseCase cancelOrder,
            StockCommittedEventHandler stockCommittedHandler,
            StockCommitFailedEventHandler stockCommitFailedHandler) {
        this.cancelOrder = cancelOrder;
        this.stockCommittedHandler = stockCommittedHandler;
        this.stockCommitFailedHandler = stockCommitFailedHandler;
    }

    /**
     * Estoque garantido: o pedido segue em pending esperando o pagamento.
     * Projecao: stock_reservation = reserved.
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

    /**
     * Estoque foi baixado: pedido transiciona paid -> processing.
     * Publica order.confirmed na mesma transacao.
     */
    @KafkaListener(topics = "ecommerce.stock.committed.v1", groupId = "order")
    public void onStockCommitted(StockCommittedEvent event) throws InvalidEventException {
        log.debug("stock.committed recebido para pedido {}", event.orderId());
        stockCommittedHandler.handle(event.orderId());
    }

    /**
     * Estoque nao pode ser baixado apos pagamento: pedido transiciona paid -> cancelled.
     * Publica order.cancelled e order.refund.requested na mesma transacao.
     */
    @KafkaListener(topics = "ecommerce.stock.commit.failed.v1", groupId = "order")
    public void onStockCommitFailed(StockCommitFailedEvent event) throws InvalidEventException {
        log.debug("stock.commit.failed recebido para pedido {}: {}", event.orderId(), event.reason());
        stockCommitFailedHandler.handle(event.orderId(), event.productId(), event.reason());
    }
}
