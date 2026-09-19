package ecommerce_event_driven.shipment.config;

import ecommerce_event_driven.shipment.shared.messaging.InvalidEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Configuracao do Kafka para o servico shipment.
 * Error handler bloqueante com retry e DLT.
 */
@Configuration
public class KafkaConfig {

    @Bean
    NewTopic shipmentStatusChangedTopic() {
        return new NewTopic("ecommerce.shipment.status.changed.v1", 3, (short) 1);
    }

    @Bean
    NewTopic shipmentStatusChangedDltTopic() {
        return new NewTopic("ecommerce.shipment.status.changed.v1-dlt", 3, (short) 1);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    String dltTopic = record.topic() + "-dlt";
                    return new org.apache.kafka.common.TopicPartition(dltTopic, record.partition());
                });

        var backoff = new ExponentialBackOff(1_000, 2.0);
        backoff.setMaxInterval(10_000);
        backoff.setMaxElapsedTime(60_000);

        var handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(
                InvalidEventException.class,
                org.springframework.dao.DataIntegrityViolationException.class);
        return handler;
    }
}
