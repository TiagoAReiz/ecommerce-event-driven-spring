package ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.messaging.StockEventPublisherPort;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging.events.StockReservedEvent;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging.events.StockRejectedEvent;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging.events.StockCommittedEvent;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging.events.StockCommitFailedEvent;
import ecommerce_event_driven.inventory.shared.outbox.OutboxMessage;
import ecommerce_event_driven.inventory.shared.outbox.OutboxWriter;

/**
 * Publica eventos de stock na outbox para ser pickado pelo Debezium.
 * Nao publica direto no Kafka: grava na tabela outbox, e a mesma transacao
 * que gravou os dados ja tem a publicacao preparada.
 */
@Component
public class StockEventOutboxPublisher implements StockEventPublisherPort {

    private final OutboxWriter outboxWriter;

    public StockEventOutboxPublisher(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void publishStockReserved(Long idOrder) {
        StockReservedEvent event = new StockReservedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                idOrder);

        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID(),
                "stock",
                String.valueOf(idOrder),
                "ecommerce.stock.reserved.v1",
                "stockReserved",
                event);

        outboxWriter.write(message);
    }

    @Override
    public void publishStockRejected(Long idOrder, Long idProduct) {
        StockRejectedEvent event = new StockRejectedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                idOrder,
                idProduct);

        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID(),
                "stock",
                String.valueOf(idOrder),
                "ecommerce.stock.rejected.v1",
                "stockRejected",
                event);

        outboxWriter.write(message);
    }

    @Override
    public void publishStockCommitted(Long idOrder) {
        StockCommittedEvent event = new StockCommittedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                idOrder);

        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID(),
                "stock",
                String.valueOf(idOrder),
                "ecommerce.stock.committed.v1",
                "stockCommitted",
                event);

        outboxWriter.write(message);
    }

    @Override
    public void publishStockCommitFailed(Long idOrder, Long idProduct, String reason) {
        StockCommitFailedEvent event = new StockCommitFailedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                idOrder,
                idProduct,
                reason);

        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID(),
                "stock",
                String.valueOf(idOrder),
                "ecommerce.stock.commit.failed.v1",
                "stockCommitFailed",
                event);

        outboxWriter.write(message);
    }
}
