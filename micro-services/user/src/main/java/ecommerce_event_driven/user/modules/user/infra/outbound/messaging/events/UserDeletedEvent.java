package ecommerce_event_driven.user.modules.user.infra.outbound.messaging.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado quando um usuario deleta sua conta.
 * Alias para o Kafka: userDeleted.
 */
public record UserDeletedEvent(UUID eventId, Instant producedAt, Long userId, Instant deletedAt) {
}
