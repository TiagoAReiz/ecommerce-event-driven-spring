package ecommerce_event_driven.payment.modules.payment.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedEvent(
        UUID eventId,
        Instant producedAt,
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        BigDecimal totalRefunded,
        Boolean full,
        String origin,
        Instant refundedAt) {
}
