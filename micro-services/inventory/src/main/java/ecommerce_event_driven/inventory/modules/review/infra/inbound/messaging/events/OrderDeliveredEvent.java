package ecommerce_event_driven.inventory.modules.review.infra.inbound.messaging.events;

import java.time.Instant;
import java.util.List;

public record OrderDeliveredEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        Instant deliveredAt,
        List<Item> items) {

    public record Item(Long productId) {}
}
