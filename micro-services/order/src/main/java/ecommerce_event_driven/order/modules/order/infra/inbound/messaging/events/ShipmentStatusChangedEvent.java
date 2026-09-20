package ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events;

import java.time.Instant;

public record ShipmentStatusChangedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long shipmentId,
        String from,
        String to,
        String trackingCode,
        String reason,
        Instant changedAt) {
}
