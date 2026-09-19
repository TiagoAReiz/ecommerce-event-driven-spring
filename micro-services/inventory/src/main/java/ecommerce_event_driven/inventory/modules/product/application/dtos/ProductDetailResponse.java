package ecommerce_event_driven.inventory.modules.product.application.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        String price,
        Integer stock,
        Integer available,
        String rating,
        Integer ratingCount,
        CategoryResponse category,
        List<ProductPhotoResponse> photos,
        Instant createdAt,
        Instant updatedAt) {

    public record ProductPhotoResponse(
            Long id,
            String photoUrl,
            Short position) {
    }
}
