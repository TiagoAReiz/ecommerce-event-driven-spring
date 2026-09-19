package ecommerce_event_driven.payment.modules.payment.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedEvent(
        UUID eventId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        Instant producedAt,
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        BigDecimal totalRefunded,
        Boolean full,
        String origin,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        Instant refundedAt) {
}
