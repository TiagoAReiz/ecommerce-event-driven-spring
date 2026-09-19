package ecommerce_event_driven.inventory.shared.outbox;

import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    public OutboxWriter(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(OutboxMessage message) {
        String sql = """
                insert into outbox (id, aggregatetype, aggregateid, topic, type, payload)
                values (:id, :aggregateType, :aggregateId, :topic, :type, cast(:payload as jsonb))
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("id", message.eventId());
        params.put("aggregateType", message.aggregateType());
        params.put("aggregateId", message.aggregateId());
        params.put("topic", message.topic());
        params.put("type", message.type());
        params.put("payload", json.writeValueAsString(message.event()));

        jdbc.update(sql, params);
    }
}
