package ecommerce_event_driven.payment.shared.client;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class ServiceTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);
    private final RestClient restClient;
    private final String gatewayUrl;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant tokenExpiration;

    public ServiceTokenProvider(
            @Value("${app.gateway.url}") String gatewayUrl,
            @Value("${app.service-client.id}") String clientId,
            @Value("${app.service-client.secret}") String clientSecret) {
        this.gatewayUrl = gatewayUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = RestClient.create();
    }

    public String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiration.minusSeconds(30))) {
            return cachedToken;
        }

        try {
            String url = gatewayUrl + "/auth/service-token";
            String requestBody = String.format(
                    "{\"clientId\": \"%s\", \"clientSecret\": \"%s\"}", clientId, clientSecret);

            JsonNode response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            cachedToken = response.get("accessToken").asText();
            int expiresIn = response.get("expiresIn").asInt();
            tokenExpiration = Instant.now().plusSeconds(expiresIn);
            return cachedToken;
        } catch (Exception e) {
            log.error("Erro ao obter token de servico", e);
            throw new RuntimeException("Nao foi possivel obter token de servico", e);
        }
    }
}
