package ecommerce_event_driven.shipment.shared.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Obtem token de servico no gateway. Cacheia ate 30s antes de expirar.
 */
@Component
public class ServiceTokenProvider {
    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final String gatewayUrl;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant tokenExpireAt;

    public ServiceTokenProvider(
            RestClient restClient,
            JsonMapper objectMapper,
            @Value("${app.gateway.url}") String gatewayUrl,
            @Value("${app.service-client.id}") String clientId,
            @Value("${app.service-client.secret}") String clientSecret) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.gatewayUrl = gatewayUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public synchronized String getToken() throws JsonProcessingException {
        if (cachedToken != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(30))) {
            return cachedToken;
        }

        String body = objectMapper.createObjectNode()
                .put("clientId", clientId)
                .put("clientSecret", clientSecret)
                .toString();

        String response = restClient.post()
                .uri(gatewayUrl + "/auth/service-token")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode json = objectMapper.readTree(response);
        cachedToken = json.get("accessToken").asText();
        long expiresIn = json.get("expiresIn").asLong();
        tokenExpireAt = Instant.now().plusSeconds(expiresIn);

        return cachedToken;
    }
}
