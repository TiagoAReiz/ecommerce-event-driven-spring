package ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging;

import ecommerce_event_driven.order.modules.order.domain.models.Order;

public interface OrderEventPublisherPort {
    void publishOrderCreated(Order order);

    void publishOrderCancelled(Long idOrder, Long idCustomer, String reason);
}
