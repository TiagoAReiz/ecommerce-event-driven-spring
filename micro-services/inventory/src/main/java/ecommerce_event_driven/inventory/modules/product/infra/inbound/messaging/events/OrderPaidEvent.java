package ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events;

import java.time.Instant;
import java.util.List;

public record OrderPaidEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        Long paymentId,
        List<Item> items) {

    public record Item(Long productId, Integer quantity) {}
}
