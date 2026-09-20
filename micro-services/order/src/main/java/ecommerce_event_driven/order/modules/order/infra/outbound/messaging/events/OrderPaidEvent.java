package ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events;

import java.time.Instant;
import java.util.List;

public record OrderPaidEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        Long paymentId,
        /** Vai junto para o inventory poder baixar o estoque mesmo sem reserva previa. */
        List<Item> items
) {
    public record Item(Long productId, Integer quantity) {}
}
