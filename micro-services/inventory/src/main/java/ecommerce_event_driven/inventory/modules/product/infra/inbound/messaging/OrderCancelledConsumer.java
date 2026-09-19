package ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging;

import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.messaging.OrderCancelledConsumerPort;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.ReleaseReservationUseCase;
import ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderCancelledEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCancelledConsumer implements OrderCancelledConsumerPort {

    private final ReleaseReservationUseCase releaseReservation;

    public OrderCancelledConsumer(ReleaseReservationUseCase releaseReservation) {
        this.releaseReservation = releaseReservation;
    }

    @Override
    @KafkaListener(topics = "ecommerce.order.cancelled.v1", groupId = "inventory")
    public void onOrderCancelled(OrderCancelledEvent orderCancelledEvent) {
        releaseReservation.execute(orderCancelledEvent.orderId());
    }
}
