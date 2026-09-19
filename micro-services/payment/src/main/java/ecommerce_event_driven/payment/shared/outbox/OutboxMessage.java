package ecommerce_event_driven.payment.shared.outbox;

import java.util.UUID;

public record OutboxMessage(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String topic,
        String type,
        Object event) {
}
