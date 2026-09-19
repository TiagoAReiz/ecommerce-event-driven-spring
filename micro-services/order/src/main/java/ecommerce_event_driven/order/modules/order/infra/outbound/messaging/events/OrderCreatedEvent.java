package ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderCreatedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        BigDecimal total,
        List<Item> items
) {
    public record Item(Long productId, int quantity) {}
}
