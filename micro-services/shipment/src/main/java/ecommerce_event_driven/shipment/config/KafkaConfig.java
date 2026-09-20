package ecommerce_event_driven.shipment.config;

import ecommerce_event_driven.shipment.shared.messaging.InvalidEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

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
        // Sem isto a falha de consumo so aparece quando o registro vai para o DLT:
        // o retry fica invisivel e o consumo parece apenas lento.
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Falha ao consumir {}-{}@{} (tentativa {}): {}",
                        record.topic(), record.partition(), record.offset(), attempt,
                org.springframework.core.NestedExceptionUtils.getMostSpecificCause(ex).toString()));

        return handler;
    }
}
