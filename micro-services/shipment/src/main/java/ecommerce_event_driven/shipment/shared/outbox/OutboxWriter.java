package ecommerce_event_driven.shipment.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra um evento para publicacao. Nao publica: grava na outbox, e o
 * Debezium publica depois do commit.
 *
 * <p>MANDATORY de proposito: chamado fora de transacao, estoura. Um evento
 * gravado em transacao propria voltaria a janela de perda que a outbox fecha.
 */
@Component
public class OutboxWriter {
    private final JdbcClient jdbc;
    private final JsonMapper objectMapper;

    public OutboxWriter(JdbcClient jdbc, JsonMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(OutboxMessage message) throws JsonProcessingException {
        jdbc.sql("""
                insert into outbox (id, aggregatetype, aggregateid, topic, type, payload)
                values (:id, :aggregateType, :aggregateId, :topic, :type, cast(:payload as jsonb))
                """)
                .param("id", message.eventId())
                .param("aggregateType", message.aggregateType())
                .param("aggregateId", message.aggregateId())
                .param("topic", message.topic())
                .param("type", message.type())
                .param("payload", objectMapper.writeValueAsString(message.event()))
                .update();
    }
}
