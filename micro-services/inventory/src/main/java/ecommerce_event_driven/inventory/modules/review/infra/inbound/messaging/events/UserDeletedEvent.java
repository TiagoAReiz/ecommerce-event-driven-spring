package ecommerce_event_driven.inventory.modules.review.infra.inbound.messaging.events;

import java.time.Instant;

public record UserDeletedEvent(
        String eventId,
        Instant producedAt,
        Long userId,
        Instant deletedAt) {
}
