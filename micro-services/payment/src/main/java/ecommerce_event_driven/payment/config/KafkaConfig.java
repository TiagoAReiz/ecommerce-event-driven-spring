package ecommerce_event_driven.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

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
