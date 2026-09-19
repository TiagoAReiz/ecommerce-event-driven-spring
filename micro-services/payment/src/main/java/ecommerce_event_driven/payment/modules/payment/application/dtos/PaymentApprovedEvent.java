package ecommerce_event_driven.payment.modules.payment.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentApprovedEvent(
        UUID eventId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        Instant producedAt,
        Long orderId,
        Long paymentId,
        String externalId,
        BigDecimal amount,
        String method,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        Instant approvedAt) {
}
