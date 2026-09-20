package ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events;

import java.time.Instant;

public record StockCommittedEvent(
        String eventId,
        Instant producedAt,
        Long orderId) {
}
