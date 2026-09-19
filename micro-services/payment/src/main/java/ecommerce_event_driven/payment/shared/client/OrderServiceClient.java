package ecommerce_event_driven.payment.shared.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class OrderServiceClient {
    private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);
    private final RestClient restClient;
    private final String orderServiceUrl;
    private final ServiceTokenProvider tokenProvider;

    public OrderServiceClient(
            @Value("${app.services.order.url}") String orderServiceUrl,
            ServiceTokenProvider tokenProvider) {
        this.orderServiceUrl = orderServiceUrl;
        this.tokenProvider = tokenProvider;
        this.restClient = RestClient.create();
    }

    public JsonNode getOrder(Long orderId) {
        try {
            String token = tokenProvider.getToken();
            String url = orderServiceUrl + "/internal/orders/" + orderId;
            return restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Erro ao buscar ordem {}", orderId, e);
            return null;
        }
    }
}
