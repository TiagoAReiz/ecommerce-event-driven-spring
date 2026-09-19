package ecommerce_event_driven.inventory.modules.product.domain.models;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Product(
        Long id,
        Long idCategory,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        BigDecimal rating,
        Integer ratingCount,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
