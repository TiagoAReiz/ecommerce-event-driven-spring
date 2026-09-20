package ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRefundedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        BigDecimal totalRefunded,
        boolean full,
        String origin,
        Instant refundedAt) {
}
