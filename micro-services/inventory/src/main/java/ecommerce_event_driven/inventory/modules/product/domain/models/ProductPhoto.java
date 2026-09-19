package ecommerce_event_driven.inventory.modules.product.domain.models;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record ProductPhoto(
        Long id,
        Long idProduct,
        String photoUrl,
        /** Ordem de exibicao. */
        Short position,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
