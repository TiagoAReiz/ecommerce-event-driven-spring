package ecommerce_event_driven.user.modules.user.infra.outbound.messaging;

import ecommerce_event_driven.user.modules.user.application.ports.outbound.messaging.UserEventPublisherPort;
import ecommerce_event_driven.user.modules.user.infra.outbound.messaging.events.UserDeletedEvent;
import ecommerce_event_driven.user.shared.outbox.OutboxMessage;
import ecommerce_event_driven.user.shared.outbox.OutboxWriter;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador que publica eventos de usuario na outbox.
 * A transacao fica a cargo do caso de uso que chamar.
 */
@Component
public class UserEventOutboxPublisher implements UserEventPublisherPort {

    private static final String USER_DELETED_TOPIC = "ecommerce.user.deleted.v1";
    private static final String USER_DELETED_TYPE = "userDeleted";

    private final OutboxWriter outboxWriter;

    public UserEventOutboxPublisher(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void publishUserDeleted(Long userId, Instant deletedAt) {
        UserDeletedEvent event = new UserDeletedEvent(UUID.randomUUID(), Instant.now(), userId, deletedAt);
        OutboxMessage message = new OutboxMessage(
                event.eventId(),
                "user",
                userId.toString(),
                USER_DELETED_TOPIC,
                USER_DELETED_TYPE,
                event);
        outboxWriter.write(message);
    }
}
