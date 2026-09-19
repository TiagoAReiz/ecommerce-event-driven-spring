package ecommerce_event_driven.order.shared.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Le o JWT injetado via @AuthenticationPrincipal Jwt.
 * Extrai id (claim sub convertido para Long) e roles (claim roles).
 */
@Component
public class CurrentUser {

    /**
     * Extrai o id do usuario a partir do claim 'sub' do JWT.
     * Retorna o valor numerico que sempre existe em tokens internos.
     */
    public Long getId(Jwt jwt) {
        String sub = jwt.getClaimAsString("sub");
        try {
            return Long.parseLong(sub);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Claim 'sub' nao e um numero valido: " + sub);
        }
    }

    /**
     * Extrai os papeis (roles) do JWT.
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(Jwt jwt) {
        Object rolesObj = jwt.getClaim("roles");
        if (rolesObj == null) {
            return List.of();
        }
        if (rolesObj instanceof List<?>) {
            return (List<String>) rolesObj;
        }
        if (rolesObj instanceof String str) {
            return List.of(str);
        }
        return List.of();
    }

    /**
     * Verifica se o usuario tem o papel especificado.
     */
    public boolean hasRole(Jwt jwt, String role) {
        return getRoles(jwt).contains(role);
    }

    /**
     * Extrai escopos a partir do claim 'scope' do JWT.
     * Retorna lista de escopos separados por espaco.
     */
    public List<String> getScopes(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return List.of(scope.split(" "));
    }

    /**
     * Verifica se o usuario tem o escopo especificado.
     */
    public boolean hasScope(Jwt jwt, String scope) {
        return getScopes(jwt).contains(scope);
    }
}
