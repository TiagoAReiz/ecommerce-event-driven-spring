package ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events;

import java.time.Instant;

public record OrderDeliveredEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        Instant deliveredAt
) {}
