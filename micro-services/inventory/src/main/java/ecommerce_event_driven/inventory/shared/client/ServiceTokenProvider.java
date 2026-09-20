package ecommerce_event_driven.inventory.shared.client;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Obtem e cacheia token de servico do gateway (ate 30s antes de expirar).
 * REST POST {app.gateway.url}/auth/service-token com clientId e clientSecret.
 */
@Component
public class ServiceTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(ServiceTokenProvider.class);

    private final RestClient gatewayClient;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public ServiceTokenProvider(
            @Value("${app.gateway.url}") String gatewayUrl,
            @Value("${app.service-client.id}") String clientId,
            @Value("${app.service-client.secret}") String clientSecret) {

        // RestClient.Builder nao e um bean disponivel aqui; o cliente e montado direto.
        this.gatewayClient = RestClient.builder().baseUrl(gatewayUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Retorna token valido, buscando novo se o cache expirou (30s antes).
     */
    public String getToken() {
        if (isTokenValid()) {
            return cachedToken;
        }

        try {
            TokenResponse response = gatewayClient
                    .post()
                    .uri("/auth/service-token")
                    .body(new TokenRequest(clientId, clientSecret))
                    .retrieve()
                    .body(TokenResponse.class);

            if (response != null && response.accessToken() != null) {
                cachedToken = response.accessToken();
                // Cacheia ate 30s antes de expirar
                tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn() - 30);
                return cachedToken;
            }
        } catch (Exception ex) {
            logger.error("Erro ao obter token de servico", ex);
        }

        throw new RuntimeException("Nao conseguiu obter token de servico");
    }

    private boolean isTokenValid() {
        return cachedToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt);
    }

    record TokenRequest(String clientId, String clientSecret) {
    }

    record TokenResponse(String accessToken, long expiresIn) {
    }
}
