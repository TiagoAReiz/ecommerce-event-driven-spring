package ecommerce_event_driven.shipment.shared.security;

import java.util.Collection;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extrai identidade e papeis do token JWT do gateway.
 * O claim sub contem: id do usuario, "anonymous", ou "svc:<id>".
 */
public class CurrentUser {
    private final Jwt jwt;

    public CurrentUser(Jwt jwt) {
        this.jwt = jwt;
    }

    public Long getId() {
        String sub = jwt.getClaimAsString("sub");
        if ("anonymous".equals(sub)) {
            return null;
        }
        if (sub != null && sub.startsWith("svc:")) {
            return Long.parseLong(sub.substring(4));
        }
        return Long.parseLong(sub);
    }

    public Collection<String> getRoles() {
        return jwt.getClaimAsStringList("roles");
    }

    public boolean hasRole(String role) {
        Collection<String> roles = getRoles();
        return roles != null && roles.contains(role);
    }

    public Jwt getJwt() {
        return jwt;
    }
}
