package ecommerce_event_driven.inventory.modules.review.application.ports.outbound.repos;

import ecommerce_event_driven.inventory.modules.review.domain.models.ReviewEligibility;

/**
 * Elegibilidade concedida pelo evento OrderDelivered vindo do order.
 * Nao tem remocao logica: a tabela nao tem deleted_at.
 */
public interface ReviewEligibilityRepositoryPort {

    ReviewEligibility grant(ReviewEligibility eligibility);

    boolean isEligible(Long idUser, Long idProduct, Long idOrder);
}
