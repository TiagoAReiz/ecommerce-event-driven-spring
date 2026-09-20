package ecommerce_event_driven.inventory.modules.review.application.dtos;

import java.time.Instant;

public record PendingReviewResponse(
        Long idProduct,
        String productName,
        String productPhotoUrl,
        Long idOrder,
        Instant grantedAt) {}
