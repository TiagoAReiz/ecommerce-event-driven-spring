package ecommerce_event_driven.inventory.modules.review.application.mappers;

import ecommerce_event_driven.inventory.modules.review.domain.models.Review;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEntity;

public final class ReviewMapper {

    private ReviewMapper() {
    }

    public static Review toDomain(ReviewEntity entity) {
        if (entity == null) {
            return null;
        }
        return Review.builder()
                .id(entity.getId())
                .idUser(entity.getIdUser())
                .userName(entity.getUserName())
                .userPhotoUrl(entity.getUserPhotoUrl())
                .idProduct(entity.getIdProduct())
                .idOrder(entity.getIdOrder())
                .rate(entity.getRate() != null ? entity.getRate().shortValue() : null)
                .title(entity.getTitle())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static ReviewEntity toEntity(Review model) {
        if (model == null) {
            return null;
        }
        return ReviewEntity.builder()
                .id(model.id())
                .idUser(model.idUser())
                .userName(model.userName())
                .userPhotoUrl(model.userPhotoUrl())
                .idProduct(model.idProduct())
                .idOrder(model.idOrder())
                .rate(model.rate() != null ? model.rate().intValue() : null)
                .title(model.title())
                .description(model.description())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
