package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityEntity;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * A chave e a propria elegibilidade: existsById(...) responde se aquele usuario
 * pode avaliar aquele produto por conta daquele pedido.
 */
@Repository
public interface ReviewEligibilityJpaRepository
        extends JpaRepository<ReviewEligibilityEntity, ReviewEligibilityId> {
}
