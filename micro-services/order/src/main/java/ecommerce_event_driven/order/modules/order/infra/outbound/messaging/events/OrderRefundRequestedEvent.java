package ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events;

import java.time.Instant;

public record OrderRefundRequestedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        Long paymentId,
        String reason
) {}
