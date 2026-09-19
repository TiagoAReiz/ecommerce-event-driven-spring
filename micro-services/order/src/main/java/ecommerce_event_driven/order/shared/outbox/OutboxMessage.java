package ecommerce_event_driven.order.shared.outbox;

import java.util.UUID;

/**
 * Mensagem de outbox a ser gravada na tabela outbox.
 * O evento e serializado para JSONB.
 */
public record OutboxMessage(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String topic,
        String type,
        Object event
) {
    public OutboxMessage {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId nao pode ser null");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType obrigatorio");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId obrigatorio");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic obrigatorio");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type obrigatorio");
        }
        if (event == null) {
            throw new IllegalArgumentException("event nao pode ser null");
        }
    }
}
