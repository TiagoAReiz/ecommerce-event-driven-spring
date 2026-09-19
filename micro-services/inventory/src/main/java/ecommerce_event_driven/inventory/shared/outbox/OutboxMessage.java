package ecommerce_event_driven.inventory.shared.outbox;

import java.util.UUID;

/**
 * Mensagem a ser gravada na outbox para publicacao pelo Debezium.
 */
public record OutboxMessage(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String topic,
        String type,
        Object event) {
}
