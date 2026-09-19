package ecommerce_event_driven.inventory.modules.review.domain.models;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Review(
        Long id,
        Long idUser,
        /** [snapshot] */
        String userName,
        /** [snapshot] */
        String userPhotoUrl,
        Long idProduct,
        Long idOrder,
        Short rate,
        String title,
        String description,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
