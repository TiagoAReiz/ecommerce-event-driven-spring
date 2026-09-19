package ecommerce_event_driven.inventory.modules.product.application.ports.inbound.messaging;

import ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderCreatedEvent;
import org.springframework.stereotype.Component;


public interface OrderCreatedConsumerPort {
    void onorderCreated(OrderCreatedEvent orderCreatedEvent);
}
