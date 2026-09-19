package ecommerce_event_driven.user.shared.outbox;

import java.util.UUID;

/**
 * Registro para gravar na tabela outbox. O Debezium le esta linha
 * e publica os campos como mensagem Kafka.
 */
public record OutboxMessage(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String topic,
        String type,
        Object event) {
}
