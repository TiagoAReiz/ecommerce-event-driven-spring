package ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events;

import java.time.Instant;

public record StockRejectedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long productId) {
}
