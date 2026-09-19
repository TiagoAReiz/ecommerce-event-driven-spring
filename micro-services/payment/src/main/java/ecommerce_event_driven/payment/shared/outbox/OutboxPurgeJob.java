package ecommerce_event_driven.payment.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxPurgeJob.class);
    private final JdbcClient jdbc;

    public OutboxPurgeJob(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void purgeOldOutboxMessages() {
        int deleted = jdbc.sql("""
                DELETE FROM outbox
                WHERE created_at < (now() - interval '7 days')
                """).update();
        log.info("Limpeza de outbox: {} mensagens deletadas", deleted);
    }
}
