package ecommerce_event_driven.user.modules.owner.domain.models;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Owner(
        Long id,
        Long idUser,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
