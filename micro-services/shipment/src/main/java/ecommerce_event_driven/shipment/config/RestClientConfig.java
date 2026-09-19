package ecommerce_event_driven.shipment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configura RestClient para chamadas HTTP.
 */
@Configuration
public class RestClientConfig {
    @Bean
    RestClient restClient() {
        return RestClient.builder().build();
    }
}
