package ecommerce_event_driven.inventory.shared.outbox;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Job diario que apaga eventos da outbox com mais de 7 dias.
 */
@Component
public class OutboxPurgeJob {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPurgeJob.class);

    private final NamedParameterJdbcTemplate jdbc;

    public OutboxPurgeJob(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 0 2 * * *") // todos os dias as 02:00
    public void purgeOldOutboxEntries() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        int deleted = jdbc.update(
                "DELETE FROM outbox WHERE created_at < :cutoff",
                Map.of("cutoff", sevenDaysAgo));

        if (deleted > 0) {
            logger.info("Removed {} outbox entries older than 7 days", deleted);
        }
    }
}
