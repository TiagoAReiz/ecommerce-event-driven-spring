package ecommerce_event_driven.shipment.shared.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job que deleta eventos de outbox antigos (mais de 7 dias).
 * Executa diariamente.
 */
@Component
public class OutboxPurgeJob {
    private final JdbcClient jdbc;

    public OutboxPurgeJob(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void purgeOldEvents() {
        int deleted = jdbc.sql("""
                delete from outbox
                where created_at < now() - interval '7 days'
                """)
                .update();

        if (deleted > 0) {
            System.out.println("Outbox purge: deleted " + deleted + " events");
        }
    }
}
