package ecommerce_event_driven.user.shared.security;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Le o JWT da requisicao (@AuthenticationPrincipal Jwt) e oferece
 * acesso estruturado aos claims que importam.
 */
@Component
public class CurrentUser {

    /**
     * Extrai o id do usuario do claim 'sub' (source of truth de quem age).
     * Nunca confia em userId do corpo da requisicao.
     */
    public Long extractUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String sub = jwt.getClaimAsString("sub");
        try {
            return Long.parseLong(sub);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extrai os papeis (roles) do token. Vem como uma lista no claim 'roles'.
     */
    public List<String> extractRoles(Jwt jwt) {
        if (jwt == null) {
            return List.of();
        }
        return jwt.getClaimAsStringList("roles");
    }
}
