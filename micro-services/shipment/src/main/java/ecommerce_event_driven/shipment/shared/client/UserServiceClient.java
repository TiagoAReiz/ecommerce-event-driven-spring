package ecommerce_event_driven.shipment.shared.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ecommerce_event_driven.shipment.shared.web.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente para chamadas ao servico user com autenticacao de servico.
 */
@Component
public class UserServiceClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String userServiceUrl;
    private final ServiceTokenProvider tokenProvider;

    public UserServiceClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${app.services.user.url}") String userServiceUrl,
            ServiceTokenProvider tokenProvider) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.userServiceUrl = userServiceUrl;
        this.tokenProvider = tokenProvider;
    }

    public AddressDto getAddress(Long addressId, Long userId) throws JsonProcessingException {
        String token = tokenProvider.getToken();

        String response = restClient.get()
                .uri(userServiceUrl + "/internal/addresses/{id}?userId={userId}", addressId, userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> {
                    throw new NotFoundException("Endereco nao encontrado");
                })
                .body(String.class);

        if (response == null) {
            throw new NotFoundException("Endereco nao encontrado");
        }

        JsonNode node = objectMapper.readTree(response);
        return new AddressDto(
                node.get("zipcode").asText(),
                node.get("country").asText(),
                node.get("state").asText(),
                node.get("city").asText(),
                node.get("street").asText(),
                node.get("number").asText());
    }

    public record AddressDto(String zipcode, String country, String state, String city, String street, String number) {}
}
