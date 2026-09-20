package ecommerce_event_driven.order.shared.client;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Obtem e cacheia token de servico no gateway.
 * Token com escopo internal:hydrate, TTL curto (1 min).
 * Cache local ate 30s antes de expirar para evitar chamadas repetidas.
 */
@Component
public class ServiceTokenProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceTokenProvider.class);

    private String gatewayUrl;
    private ServiceClientConfig serviceClient;

    public static class ServiceClientConfig {
        private String id;
        private String secret;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public ServiceTokenProvider(
            @org.springframework.beans.factory.annotation.Value("${app.gateway.url}") String gatewayUrl,
            @org.springframework.beans.factory.annotation.Value("${app.service-client.id}") String clientId,
            @org.springframework.beans.factory.annotation.Value("${app.service-client.secret}") String clientSecret) {
        this.gatewayUrl = gatewayUrl;
        ServiceClientConfig config = new ServiceClientConfig();
        config.setId(clientId);
        config.setSecret(clientSecret);
        this.serviceClient = config;
    }

    private String cachedToken;
    private Instant cachedUntil;

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public void setServiceClient(ServiceClientConfig serviceClient) {
        this.serviceClient = serviceClient;
    }

    /**
     * Obtem token de servico, usando cache se disponivel.
     * Retorna null se falhar na obtencao.
     */
    public String getToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedUntil)) {
            return cachedToken;
        }

        try {
            var request = new TokenRequest(serviceClient.id, serviceClient.secret);
            var client = RestClient.builder()
                    .baseUrl(gatewayUrl)
                    .build();
            var response = client.post()
                    .uri("/auth/service-token")
                    .body(request)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response != null && response.accessToken != null) {
                cachedToken = response.accessToken;
                // Cache ate 30s antes de expirar
                int expiresIn = response.expiresIn != null ? response.expiresIn : 60;
                cachedUntil = Instant.now().plusSeconds(Math.max(0, expiresIn - 30));
                return cachedToken;
            }
        } catch (Exception e) {
            LOG.warn("Falha ao obter token de servico: {}", e.getMessage());
        }
        return null;
    }

    public record TokenRequest(String clientId, String clientSecret) {}

    public record TokenResponse(String accessToken, Integer expiresIn) {}
}
