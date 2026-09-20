package ecommerce_event_driven.inventory.modules.review.application.ports.inbound.usecases;

import java.util.List;

public interface GrantReviewEligibilityUseCase {

    void grantEligibility(Long idUser, List<Long> productIds, Long idOrder);
}
