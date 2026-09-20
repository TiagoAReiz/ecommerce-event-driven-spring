package ecommerce_event_driven.inventory.modules.review.application.usecases;

import ecommerce_event_driven.inventory.modules.review.application.ports.inbound.usecases.AnonymizeUserReviewsUseCase;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.ReviewJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnonymizeUserReviewsService implements AnonymizeUserReviewsUseCase {

    private static final Logger log = LoggerFactory.getLogger(AnonymizeUserReviewsService.class);

    private final ReviewJpaRepository jpaRepository;

    public AnonymizeUserReviewsService(ReviewJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void anonymizeReviews(Long idUser) {
        int updated = jpaRepository.anonymizeByUser(idUser);
        log.info("Reviews anonimizadas do usuario {}: {} registros", idUser, updated);
    }
}
