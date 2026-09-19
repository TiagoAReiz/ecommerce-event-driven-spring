package ecommerce_event_driven.payment.modules.payment.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record OrderRefundRequestedEvent(
        UUID eventId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        Instant producedAt,
        Long orderId,
        Long paymentId,
        String reason) {
}
