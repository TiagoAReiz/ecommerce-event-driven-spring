package ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging.events;

import java.time.Instant;

public record StockCommitFailedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long productId,
        String reason) {
}
