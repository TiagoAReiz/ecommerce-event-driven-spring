package ecommerce_event_driven.inventory.shared.security;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Le o JWT (@AuthenticationPrincipal Jwt) e extrai id (claim sub),
 * roles (claim roles) e escopos.
 */
public record CurrentUser(
        Long id,
        List<String> roles,
        List<String> scopes) {

    /**
     * Extrai CurrentUser do JWT fornecido pela anotacao @AuthenticationPrincipal.
     */
    public static CurrentUser from(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        String subClaim = jwt.getClaimAsString("sub");
        Long userId = null;

        // sub pode ser "anonymous", "svc:<id>" ou um numero
        if (subClaim != null && !subClaim.equals("anonymous")) {
            if (subClaim.startsWith("svc:")) {
                try {
                    userId = Long.parseLong(subClaim.substring(4));
                } catch (NumberFormatException e) {
                    // Se nao conseguir parsear, deixa null
                }
            } else {
                try {
                    userId = Long.parseLong(subClaim);
                } catch (NumberFormatException e) {
                    // Se nao conseguir parsear, deixa null
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<String> roles = jwt.getClaimAsStringList("roles");
        @SuppressWarnings("unchecked")
        List<String> scopes = jwt.getClaimAsStringList("scope");

        return new CurrentUser(userId, roles != null ? roles : List.of(), scopes != null ? scopes : List.of());
    }
}
