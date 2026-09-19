package ecommerce_event_driven.api_gateway.modules.auth.application.dtos;

import java.time.Instant;

/** Token recem assinado, com o instante em que expira. */
public record IssuedToken(
        String value,
        Instant expiresAt) {
}
