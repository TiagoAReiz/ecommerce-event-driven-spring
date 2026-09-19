package ecommerce_event_driven.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.ExponentialBackOff;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;

/**
 * Configuracao Kafka: error handler com DLT, tópicos de stock.
 */
@Configuration
public class KafkaConfig {

    /**
     * Error handler bloqueante com retry exponencial e DLT (Dead Letter Topic).
     * Falhas permanentes nao sao retentaveis: DeserializationException,
     * InvalidEventException, DataIntegrityViolationException.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        // Mesma particao no -dlt: o reprocessamento manual preserva a ordem por pedido.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + "-dlt", record.partition()));

        ExponentialBackOff backoff = new ExponentialBackOff(1_000, 2.0);
        backoff.setMaxInterval(10_000);
        backoff.setMaxElapsedTime(60_000); // 1s, 2s, 4s, 8s, 10s... desiste em ~1 min

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        // Falha permanente: tentar de novo so atrasa a particao inteira.
        handler.addNotRetryableExceptions(
                InvalidEventException.class,
                DataIntegrityViolationException.class);
        return handler;
    }

    // Tópicos que o serviço de inventory PRODUZ

    @Bean
    public NewTopic stockReservedTopic() {
        return new NewTopic("ecommerce.stock.reserved.v1", 3, (short) 1);
    }

    @Bean
    public NewTopic stockReservedDltTopic() {
        return new NewTopic("ecommerce.stock.reserved.v1-dlt", 3, (short) 1);
    }

    @Bean
    public NewTopic stockRejectedTopic() {
        return new NewTopic("ecommerce.stock.rejected.v1", 3, (short) 1);
    }

    @Bean
    public NewTopic stockRejectedDltTopic() {
        return new NewTopic("ecommerce.stock.rejected.v1-dlt", 3, (short) 1);
    }

    @Bean
    public NewTopic stockCommittedTopic() {
        return new NewTopic("ecommerce.stock.committed.v1", 3, (short) 1);
    }

    @Bean
    public NewTopic stockCommittedDltTopic() {
        return new NewTopic("ecommerce.stock.committed.v1-dlt", 3, (short) 1);
    }

    @Bean
    public NewTopic stockCommitFailedTopic() {
        return new NewTopic("ecommerce.stock.commit.failed.v1", 3, (short) 1);
    }

    @Bean
    public NewTopic stockCommitFailedDltTopic() {
        return new NewTopic("ecommerce.stock.commit.failed.v1-dlt", 3, (short) 1);
    }
}
