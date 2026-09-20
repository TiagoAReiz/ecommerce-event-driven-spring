package ecommerce_event_driven.inventory.modules.review.application.dtos;

import java.util.List;
import java.util.Map;

public record ReviewsWithSummaryResponse(
        List<ReviewResponse> content,
        PageResponse page,
        SummaryResponse summary) {

    public record SummaryResponse(
            String rating,
            Integer ratingCount,
            Map<Integer, Long> distribution) {}
}
