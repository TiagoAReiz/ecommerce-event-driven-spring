package ecommerce_event_driven.user.shared.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

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
    private final JsonMapper json;

    public OutboxWriter(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(OutboxMessage message) {
        jdbc.sql("""
                insert into outbox (id, aggregatetype, aggregateid, topic, type, payload)
                values (:id, :aggregateType, :aggregateId, :topic, :type, cast(:payload as jsonb))
                """)
                .param("id", message.eventId())
                .param("aggregateType", message.aggregateType())
                .param("aggregateId", message.aggregateId())
                .param("topic", message.topic())
                .param("type", message.type())
                .param("payload", json.writeValueAsString(message.event()))
                .update();
    }
}
