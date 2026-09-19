package ecommerce_event_driven.order.shared.outbox;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Job agendado que limpa eventos da outbox apos 7 dias.
 * Executa diariamente: 86400000ms = 24 horas.
 */
@Component
public class OutboxPurgeJob {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxPurgeJob.class);

    private final JdbcClient jdbcClient;

    public OutboxPurgeJob(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Scheduled(fixedDelay = 86400000)  // 24 horas
    public void purgeOldEvents() {
        try {
            // Remove eventos com mais de 7 dias
            Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

            String sql = "DELETE FROM outbox WHERE created_at < ?";
            int deleted = jdbcClient.sql(sql)
                    .param(sevenDaysAgo)
                    .update();

            if (deleted > 0) {
                LOG.info("Limpeza da outbox concluida: {} eventos removidos", deleted);
            }
        } catch (Exception e) {
            LOG.error("Erro ao limpar outbox: {}", e.getMessage(), e);
        }
    }
}
