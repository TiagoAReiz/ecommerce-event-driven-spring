package ecommerce_event_driven.order.shared.outbox;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grava mensagens na tabela outbox dentro de uma transacao obrigatoria (MANDATORY).
 * O Debezium lê o WAL e publica cada mensagem no Kafka.
 *
 * A propagacao MANDATORY garante que so pode ser chamado dentro de uma transacao existente,
 * e que o evento e commitado junto com a mudanca de estado que o gerou.
 */
@Component
public class OutboxWriter {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxWriter.class);

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public OutboxWriter(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Grava a mensagem na outbox dentro da transacao atual (obrigatoria).
     * Usa cast(? as jsonb) para a coluna payload.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(OutboxMessage message) {
        try {
            String payload = jsonMapper.writeValueAsString(message.event());

            String sql = """
                    INSERT INTO outbox (id, aggregatetype, aggregateid, topic, type, payload)
                    VALUES (?, ?, ?, ?, ?, cast(? as jsonb))
                    """;

            jdbcClient.sql(sql)
                    .param(message.eventId())
                    .param(message.aggregateType())
                    .param(message.aggregateId())
                    .param(message.topic())
                    .param(message.type())
                    .param(payload)
                    .update();

            LOG.debug("Evento gravado na outbox: {} - {}", message.type(), message.eventId());
        } catch (Exception e) {
            LOG.error("Falha ao gravar evento na outbox: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao gravar evento na outbox", e);
        }
    }
}
