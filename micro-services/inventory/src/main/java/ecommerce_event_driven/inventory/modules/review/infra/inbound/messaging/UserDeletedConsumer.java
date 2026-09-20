package ecommerce_event_driven.inventory.modules.review.infra.inbound.messaging;

import ecommerce_event_driven.inventory.config.InvalidEventException;
import ecommerce_event_driven.inventory.modules.review.application.ports.inbound.usecases.AnonymizeUserReviewsUseCase;
import ecommerce_event_driven.inventory.modules.review.infra.inbound.messaging.events.UserDeletedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserDeletedConsumer {

    private final AnonymizeUserReviewsUseCase anonymizeReviews;

    public UserDeletedConsumer(AnonymizeUserReviewsUseCase anonymizeReviews) {
        this.anonymizeReviews = anonymizeReviews;
    }

    @KafkaListener(topics = "ecommerce.user.deleted.v1", groupId = "inventory")
    public void onUserDeleted(UserDeletedEvent event) {
        if (event == null || event.userId() == null) {
            throw new InvalidEventException("UserDeletedEvent invalido");
        }

        anonymizeReviews.anonymizeReviews(event.userId());
    }
}
