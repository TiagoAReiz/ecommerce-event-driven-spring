package ecommerce_event_driven.api_gateway.modules.auth.infra.outbound.external;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.CreateUserRequest;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.UserResponse;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.external.UserMicroservicePort;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security.TokenIssuerPort;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Estas chamadas acontecem durante o login, quando o usuario ainda nao tem
 * token. Quem se autentica aqui e o gateway, com um token de servico de vida
 * curta emitido na hora.
 */
@Service
public class UserMicroservice implements UserMicroservicePort {

    private final RestClient restClient;
    private final TokenIssuerPort tokenIssuer;

    public UserMicroservice(
            @Value("${app.user-microservice.url}") String baseUrl,
            TokenIssuerPort tokenIssuer) {
        this.restClient = RestClient.create(baseUrl);
        this.tokenIssuer = tokenIssuer;
    }

    /**
     * Usa exchange em vez de retrieve porque aqui 404 nao e erro: e a resposta
     * esperada no primeiro acesso do usuario. Com retrieve viraria excecao.
     */
    @Override
    public Optional<UserResponse> findByEmail(String email) {
        return restClient.get()
                .uri("/users?email={email}", email)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        return Optional.empty();
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException(
                                "user respondeu " + status + " ao buscar " + email);
                    }
                    return Optional.ofNullable(response.bodyTo(UserResponse.class));
                });
    }

    /** Qualquer resposta fora do 2xx estoura, 409 incluso. */
    @Override
    public UserResponse create(CreateUserRequest request) {
        return restClient.post()
                .uri("/users")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(request)
                .retrieve()
                .requiredBody(UserResponse.class);
    }

    @Override
    public Optional<UserResponse> findProfile(Long userId) {
        return restClient.get()
                .uri("/internal/users/{id}/profile", userId)
                .header(HttpHeaders.AUTHORIZATION, bearerForService())
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 404) {
                        return Optional.empty();
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException(
                                "user respondeu " + status + " ao buscar profile de " + userId);
                    }
                    return Optional.ofNullable(response.bodyTo(UserResponse.class));
                });
    }

    /**
     * Um token novo por chamada. Assinar RSA custa menos que a propria ida ate
     * o user, e evita ter de cuidar de cache e renovacao.
     */
    private String bearer() {
        return "Bearer " + tokenIssuer.issueForLogin().value();
    }

    /**
     * Token de servico para chamadas servidor-a-servidor.
     */
    private String bearerForService() {
        return "Bearer " + tokenIssuer.issueForService("api-gateway").value();
    }
}
