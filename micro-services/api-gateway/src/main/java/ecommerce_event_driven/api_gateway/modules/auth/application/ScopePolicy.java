package ecommerce_event_driven.api_gateway.modules.auth.application;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Define o conjunto de escopos que cada papel de usuario tem acesso.
 * Simplifica a emissao de token interno: em vez de resolver escopo por rota,
 * o token carrega todos os escopos do papel e o servico valida.
 */
@Component
public class ScopePolicy {

    private static final Set<String> ANONYMOUS_SCOPES = Set.of(
            "catalog:read",
            "users:read",
            "reviews:read",
            "shipments:read",
            "payments:read"
    );

    private static final Set<String> CUSTOMER_SCOPES = Set.copyOf(new HashSet<String>(ANONYMOUS_SCOPES) {{
        add("users:write");
        add("addresses:read");
        add("addresses:write");
        add("reviews:write");
        add("cart:read");
        add("cart:write");
        add("orders:read");
        add("orders:write");
        add("payments:write");
        add("shipments:write");
    }});

    private static final Set<String> OWNER_SCOPES = Set.copyOf(new HashSet<String>(CUSTOMER_SCOPES) {{
        add("catalog:write");
        add("sales:read");
        add("payments:refund");
    }});

    public Set<String> scopesForAnonymous() {
        return ANONYMOUS_SCOPES;
    }

    public Set<String> scopesForRoles(java.util.List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return scopesForAnonymous();
        }
        // Resolve o escopo maximo entre os papeis.
        // Ordem de precedencia: owner > customer > anonymous
        if (roles.contains("owner")) {
            return OWNER_SCOPES;
        }
        if (roles.contains("customer")) {
            return CUSTOMER_SCOPES;
        }
        return scopesForAnonymous();
    }
}
