package ecommerce_event_driven.inventory.modules.review.application.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull Long idOrder,
        @NotNull @Min(1) @Max(5) Integer rate,
        @Size(max = 150) String title,
        String description) {}
