package ecommerce_event_driven.payment.shared.security;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Quem esta chamando, lido do token interno da requisicao atual.
 *
 * <p>Singleton que le o SecurityContext a cada chamada: o Jwt nao e um bean, entao nao da
 * para injeta-lo no construtor, nem com escopo de requisicao.
 */
@Component
public class CurrentUser {

    /** Id do usuario (claim sub); null para anonimo ou token de servico. */
    public Long getId() {
        Jwt jwt = jwt();
        if (jwt == null) {
            return null;
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Set<String> getRoles() {
        Jwt jwt = jwt();
        if (jwt == null) {
            return Set.of();
        }
        Object roles = jwt.getClaim("roles");
        if (roles instanceof List<?> list) {
            return new HashSet<>((List<String>) list);
        }
        return Set.of();
    }

    public boolean isOwner() {
        return getRoles().contains("owner");
    }

    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        return null;
    }
}
