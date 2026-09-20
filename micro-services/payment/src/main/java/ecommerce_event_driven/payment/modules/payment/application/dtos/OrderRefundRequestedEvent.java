package ecommerce_event_driven.payment.modules.payment.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record OrderRefundRequestedEvent(
        UUID eventId,
        Instant producedAt,
        Long orderId,
        Long paymentId,
        String reason) {
}
