package ecommerce_event_driven.payment.modules.payment.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentApprovedEvent(
        UUID eventId,
        Instant producedAt,
        Long orderId,
        Long paymentId,
        String externalId,
        BigDecimal amount,
        String method,
        Instant approvedAt) {
}
