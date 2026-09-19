package ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.external;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.CreateUserRequest;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.UserResponse;
import java.util.Optional;

/** Acesso ao microservico user. Ele e o dono dos dados de usuario. */
public interface UserMicroservicePort {

    /** Optional vazio = 404, o usuario ainda nao existe. */
    Optional<UserResponse> findByEmail(String email);

    /** Estoura se o user recusar, 409 incluso. */
    UserResponse create(CreateUserRequest request);
}
