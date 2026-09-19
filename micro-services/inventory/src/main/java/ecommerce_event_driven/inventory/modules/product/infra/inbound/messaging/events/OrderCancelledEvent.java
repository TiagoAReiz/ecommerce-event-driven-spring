package ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events;

import java.time.Instant;

public record OrderCancelledEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        String reason) {
}
