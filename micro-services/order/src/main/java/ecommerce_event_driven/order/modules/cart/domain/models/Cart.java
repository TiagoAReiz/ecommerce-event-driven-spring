package ecommerce_event_driven.order.modules.cart.domain.models;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Cart(
        Long id,
        Long idUser,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
