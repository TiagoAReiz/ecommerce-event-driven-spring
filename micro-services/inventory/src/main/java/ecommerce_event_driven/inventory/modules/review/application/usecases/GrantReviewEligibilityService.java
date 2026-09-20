package ecommerce_event_driven.inventory.modules.review.application.usecases;

import ecommerce_event_driven.inventory.modules.review.application.ports.inbound.usecases.GrantReviewEligibilityUseCase;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.ReviewEligibilityJpaRepository;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityEntity;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityId;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrantReviewEligibilityService implements GrantReviewEligibilityUseCase {

    private static final Logger log = LoggerFactory.getLogger(GrantReviewEligibilityService.class);

    private final ReviewEligibilityJpaRepository jpaRepository;

    public GrantReviewEligibilityService(ReviewEligibilityJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void grantEligibility(Long idUser, List<Long> productIds, Long idOrder) {
        int inserted = 0;
        for (Long productId : productIds) {
            try {
                // INSERT ON CONFLICT DO NOTHING via banco
                // Spring nao tem suporte nativo, entao verificamos antes
                if (jpaRepository.findByIdUserAndIdProductAndIdOrder(idUser, productId, idOrder).isEmpty()) {
                    ReviewEligibilityId id = new ReviewEligibilityId(idUser, productId, idOrder);
                    jpaRepository.save(ReviewEligibilityEntity.builder()
                            .id(id)
                            .grantedAt(Instant.now())
                            .build());
                    inserted++;
                }
            } catch (Exception e) {
                // Ignora constraint unique
                log.debug("Elegibilidade ja existia ou erro: {}", e.getMessage());
            }
        }

        log.info("Elegibilidade concedida ao usuario {} para {} produtos do pedido {}", idUser, inserted, idOrder);
    }
}
