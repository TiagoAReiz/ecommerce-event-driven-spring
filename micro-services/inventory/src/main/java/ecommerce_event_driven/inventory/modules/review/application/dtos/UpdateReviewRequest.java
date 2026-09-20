package ecommerce_event_driven.inventory.modules.review.application.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateReviewRequest(
        @Min(1) @Max(5) Integer rate,
        @Size(max = 150) String title,
        String description) {}
