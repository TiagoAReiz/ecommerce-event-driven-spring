package ecommerce_event_driven.payment.shared.outbox;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

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
