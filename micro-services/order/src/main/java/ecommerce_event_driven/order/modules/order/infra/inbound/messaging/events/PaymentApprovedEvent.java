package ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentApprovedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long paymentId,
        String externalId,
        BigDecimal amount,
        String method,
        Instant approvedAt) {
}
