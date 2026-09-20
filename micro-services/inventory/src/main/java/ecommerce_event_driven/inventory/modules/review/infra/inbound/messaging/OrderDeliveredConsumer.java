package ecommerce_event_driven.inventory.modules.review.infra.inbound.messaging;

import ecommerce_event_driven.inventory.config.InvalidEventException;
import ecommerce_event_driven.inventory.modules.review.application.ports.inbound.usecases.GrantReviewEligibilityUseCase;
import ecommerce_event_driven.inventory.modules.review.infra.inbound.messaging.events.OrderDeliveredEvent;
import java.util.stream.Collectors;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderDeliveredConsumer {

    private final GrantReviewEligibilityUseCase grantEligibility;

    public OrderDeliveredConsumer(GrantReviewEligibilityUseCase grantEligibility) {
        this.grantEligibility = grantEligibility;
    }

    @KafkaListener(topics = "ecommerce.order.delivered.v1", groupId = "inventory")
    public void onOrderDelivered(OrderDeliveredEvent event) {
        if (event == null || event.orderId() == null || event.customerId() == null || event.items() == null || event.items().isEmpty()) {
            throw new InvalidEventException("OrderDeliveredEvent invalido");
        }

        var productIds = event.items().stream()
                .map(OrderDeliveredEvent.Item::productId)
                .collect(Collectors.toList());

        grantEligibility.grantEligibility(event.customerId(), productIds, event.orderId());
    }
}
