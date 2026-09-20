package ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events;

import java.time.Instant;

public record PaymentFailedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long paymentId,
        String status,
        String statusDetail) {
}
