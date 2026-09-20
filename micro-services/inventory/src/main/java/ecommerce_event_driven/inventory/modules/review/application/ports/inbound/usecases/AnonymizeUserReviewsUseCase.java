package ecommerce_event_driven.inventory.modules.review.application.ports.inbound.usecases;

public interface AnonymizeUserReviewsUseCase {

    void anonymizeReviews(Long idUser);
}
