package ecommerce_event_driven.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        var recoverer = new org.springframework.kafka.listener.DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + "-dlt", record.partition()));

        var backoff = new ExponentialBackOff(1_000, 2.0);
        backoff.setMaxInterval(10_000);
        backoff.setMaxElapsedTime(60_000);

        var handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class,
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

    @Bean
    public NewTopic paymentApprovedTopic() {
        return TopicBuilder.name("ecommerce.payment.approved.v1").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentApprovedDltTopic() {
        return TopicBuilder.name("ecommerce.payment.approved.v1-dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name("ecommerce.payment.failed.v1").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedDltTopic() {
        return TopicBuilder.name("ecommerce.payment.failed.v1-dlt").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentRefundedTopic() {
        return TopicBuilder.name("ecommerce.payment.refunded.v1").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentRefundedDltTopic() {
        return TopicBuilder.name("ecommerce.payment.refunded.v1-dlt").partitions(3).replicas(1).build();
    }
}
