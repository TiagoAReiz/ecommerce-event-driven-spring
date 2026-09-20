package ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events;

import java.time.Instant;
import java.util.List;

public record OrderDeliveredEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        Instant deliveredAt,
        /** Um produto por linha: e por eles que o inventory concede o direito de avaliar. */
        List<Item> items
) {
    public record Item(Long productId) {}
}
