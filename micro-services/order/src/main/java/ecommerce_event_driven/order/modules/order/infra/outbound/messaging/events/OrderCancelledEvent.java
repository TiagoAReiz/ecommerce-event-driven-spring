package ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events;

import java.time.Instant;

public record OrderCancelledEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        String reason) {
}
