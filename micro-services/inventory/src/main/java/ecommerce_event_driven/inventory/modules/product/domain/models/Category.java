package ecommerce_event_driven.inventory.modules.product.domain.models;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Category(
        Long id,
        String name,
        String slug,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
