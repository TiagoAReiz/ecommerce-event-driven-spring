package ecommerce_event_driven.order.config;

import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.util.backoff.ExponentialBackOff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuracao de Kafka:
 * - Error handler com retry exponencial e Dead Letter Topic (DLT)
 * - NewTopics para os topicos que order PRODUZ
 * - Excecoes nao-retentaveis enviadas direto a DLT
 */
@Configuration
@EnableKafka
public class KafkaConfig {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * Excecao customizada para eventos invalidos.
     * Nao retenivel: vai direto para DLT.
     */
    public static class InvalidEventException extends Exception {
        public InvalidEventException(String message) {
            super(message);
        }

        public InvalidEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Error handler: retry com backoff exponencial (1s, x2, max 10s, total 60s).
     * Excecoes nao-retentaveis: DeserializationException, InvalidEventException, DataIntegrityViolationException.
     */
    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer dlpr) {
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000);           // comeca em 1s
        backOff.setMultiplier(2.0);                 // dobra a cada tentativa
        backOff.setMaxInterval(10000);              // maximo de 10s
        backOff.setMaxElapsedTime(60000);           // tota de 60s

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(dlpr, backOff);
        errorHandler.addNotRetryableExceptions(
                InvalidEventException.class,
                org.springframework.kafka.support.serializer.DeserializationException.class,
                org.springframework.dao.DataIntegrityViolationException.class
        );
        return errorHandler;
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, ?> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate);
    }

    // --- Topics que order PRODUZ (com seus DLTs) ---

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name("ecommerce.order.created.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCreatedDltTopic() {
        return TopicBuilder.name("ecommerce.order.created.v1-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name("ecommerce.order.cancelled.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCancelledDltTopic() {
        return TopicBuilder.name("ecommerce.order.cancelled.v1-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderPaidTopic() {
        return TopicBuilder.name("ecommerce.order.paid.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderPaidDltTopic() {
        return TopicBuilder.name("ecommerce.order.paid.v1-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderConfirmedTopic() {
        return TopicBuilder.name("ecommerce.order.confirmed.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderConfirmedDltTopic() {
        return TopicBuilder.name("ecommerce.order.confirmed.v1-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderDeliveredTopic() {
        return TopicBuilder.name("ecommerce.order.delivered.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderDeliveredDltTopic() {
        return TopicBuilder.name("ecommerce.order.delivered.v1-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderRefundRequestedTopic() {
        return TopicBuilder.name("ecommerce.order.refund.requested.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderRefundRequestedDltTopic() {
        return TopicBuilder.name("ecommerce.order.refund.requested.v1-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
