package ecommerce_event_driven.api_gateway.modules.auth.application.dtos;

import java.util.List;

/** Usuario como o microservico user devolve. */
public record UserResponse(
        Long id,
        String name,
        String email,
        String googleSub,
        String photoUrl,
        List<String> roles) {
}
