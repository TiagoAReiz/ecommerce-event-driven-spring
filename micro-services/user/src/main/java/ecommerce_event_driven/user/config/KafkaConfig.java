package ecommerce_event_driven.user.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuracao de topicos Kafka que o servico USER produz.
 * Cada topico tem 3 particoes, 1 replica, e um correspondente -dlt
 * para mensagens que nao podem ser reprocessadas.
 */
@Configuration
public class KafkaConfig {

    public static final String USER_DELETED_TOPIC = "ecommerce.user.deleted.v1";
    public static final String USER_DELETED_DLT_TOPIC = "ecommerce.user.deleted.v1-dlt";

    @Bean
    public NewTopic userDeletedTopic() {
        return TopicBuilder.name(USER_DELETED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userDeletedDltTopic() {
        return TopicBuilder.name(USER_DELETED_DLT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
