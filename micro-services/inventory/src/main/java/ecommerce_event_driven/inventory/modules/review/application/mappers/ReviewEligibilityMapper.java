package ecommerce_event_driven.inventory.modules.review.application.mappers;

import ecommerce_event_driven.inventory.modules.review.domain.models.ReviewEligibility;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityEntity;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityId;

public final class ReviewEligibilityMapper {

    private ReviewEligibilityMapper() {
    }

    public static ReviewEligibility toDomain(ReviewEligibilityEntity entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        ReviewEligibilityId key = entity.getId();
        return ReviewEligibility.builder()
                .idUser(key.getIdUser())
                .idProduct(key.getIdProduct())
                .idOrder(key.getIdOrder())
                .grantedAt(entity.getGrantedAt())
                .build();
    }

    public static ReviewEligibilityEntity toEntity(ReviewEligibility model) {
        if (model == null) {
            return null;
        }
        return ReviewEligibilityEntity.builder()
                .id(toKey(model))
                .grantedAt(model.grantedAt())
                .build();
    }

    /** A chave composta e a propria elegibilidade: util para existsById. */
    public static ReviewEligibilityId toKey(ReviewEligibility model) {
        if (model == null) {
            return null;
        }
        return new ReviewEligibilityId(model.idUser(), model.idProduct(), model.idOrder());
    }
}
