package ecommerce_event_driven.user.modules.user.application.ports.outbound.messaging;

import java.time.Instant;

public interface UserEventPublisherPort {
    void publishUserDeleted(Long userId, Instant deletedAt);
}
