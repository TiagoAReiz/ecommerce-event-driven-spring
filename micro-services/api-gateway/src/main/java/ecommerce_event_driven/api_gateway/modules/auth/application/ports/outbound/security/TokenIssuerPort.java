package ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.IssuedToken;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.UserResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Emissao de token. Sao duas plateias diferentes, e elas nao se misturam:
 * o token que vai para o browser nao e aceito pelos servicos internos, e
 * vice-versa. Quem separa e a claim aud, validada do outro lado.
 */
public interface TokenIssuerPort {

    /** Vai para o browser. aud=front, com roles e auth_time. */
    IssuedToken issueForUser(UserResponse user, List<String> roles, Instant authTime);

    /**
     * Usado pelo proprio gateway para falar com o microservico user durante o
     * login, quando ainda nao existe usuario nenhum. aud=internal, e so com o
     * escopo de mexer em usuario: nao serve para alcancar nenhum outro servico.
     */
    IssuedToken issueForLogin();

    /** Token interno com escopos do papel para repassar ao servico. */
    IssuedToken issueInternal(String sub, List<String> roles, Set<String> scopes);

    /** Token de servico para chamadas servidor-a-servidor. */
    IssuedToken issueForService(String clientId);
}
