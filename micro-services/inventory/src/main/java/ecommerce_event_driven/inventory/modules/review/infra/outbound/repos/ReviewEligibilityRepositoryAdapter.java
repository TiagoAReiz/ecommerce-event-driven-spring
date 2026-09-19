package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.review.application.mappers.ReviewEligibilityMapper;
import ecommerce_event_driven.inventory.modules.review.application.ports.outbound.repos.ReviewEligibilityRepositoryPort;
import ecommerce_event_driven.inventory.modules.review.domain.models.ReviewEligibility;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityId;
import org.springframework.stereotype.Component;

@Component
public class ReviewEligibilityRepositoryAdapter implements ReviewEligibilityRepositoryPort {

    private final ReviewEligibilityJpaRepository jpaRepository;

    public ReviewEligibilityRepositoryAdapter(ReviewEligibilityJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ReviewEligibility grant(ReviewEligibility eligibility) {
        return ReviewEligibilityMapper.toDomain(
                jpaRepository.save(ReviewEligibilityMapper.toEntity(eligibility)));
    }

    @Override
    public boolean isEligible(Long idUser, Long idProduct, Long idOrder) {
        return jpaRepository.existsById(new ReviewEligibilityId(idUser, idProduct, idOrder));
    }
}
