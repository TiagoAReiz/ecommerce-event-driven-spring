package ecommerce_event_driven.user.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Remove registros da outbox com mais de 7 dias. Roda diariamente.
 */
@Component
public class OutboxPurgeJob {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPurgeJob.class);
    private final JdbcClient jdbc;

    public OutboxPurgeJob(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 2 * * *") // 2 AM todo dia
    public void purgeOldRecords() {
        // Intervalo no proprio SQL: um Instant como parametro nao tem tipo SQL inferivel.
        int deleted = jdbc.sql("delete from outbox where created_at < now() - interval '7 days'")
                .update();
        if (deleted > 0) {
            logger.info("Limpeza da outbox: {} registros removidos", deleted);
        }
    }
}
