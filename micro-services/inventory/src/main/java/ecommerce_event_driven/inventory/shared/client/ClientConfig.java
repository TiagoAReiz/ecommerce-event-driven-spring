package ecommerce_event_driven.inventory.shared.client;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Configuracao de RestClients para chamadas entre servicos com token de servico.
 */
@Configuration
public class ClientConfig {

    @Bean
    public RestClient userServiceRestClient(
            ServiceTokenProvider tokenProvider,
            @Value("${app.services.user.url}") String userUrl) {

        return RestClient.builder()
                .baseUrl(userUrl)
                .build();
    }

    @Bean
    public RestClient gatewayRestClient(@Value("${app.gateway.url}") String gatewayUrl) {
        return RestClient.builder()
                .baseUrl(gatewayUrl)
                .build();
    }
}
