package ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.IssuedToken;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.UserResponse;

/**
 * Emissao de token. Sao duas plateias diferentes, e elas nao se misturam:
 * o token que vai para o browser nao e aceito pelos servicos internos, e
 * vice-versa. Quem separa e a claim aud, validada do outro lado.
 */
public interface TokenIssuerPort {

    /** Vai para o browser. aud=front. */
    IssuedToken issueForUser(UserResponse user);

    /**
     * Usado pelo proprio gateway para falar com o microservico user durante o
     * login, quando ainda nao existe usuario nenhum. aud=internal, e so com o
     * escopo de mexer em usuario: nao serve para alcancar nenhum outro servico.
     */
    IssuedToken issueForLogin();
}
