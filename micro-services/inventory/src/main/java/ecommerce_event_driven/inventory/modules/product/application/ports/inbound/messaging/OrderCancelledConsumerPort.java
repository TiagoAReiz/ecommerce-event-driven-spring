package ecommerce_event_driven.inventory.modules.product.application.ports.inbound.messaging;

import ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderCancelledEvent;

public interface OrderCancelledConsumerPort {
    void onOrderCancelled(OrderCancelledEvent orderCancelledEvent);
}
