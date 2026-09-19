package ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging;

import ecommerce_event_driven.order.modules.order.domain.models.Order;

/**
 * Port para publicacao de eventos de pedido na outbox.
 * Todos os metodos publicam dentro da transacao que muda o status.
 */
public interface OrderEventPublisherPort {
    void publishOrderCreated(Order order);

    void publishOrderCancelled(Long idOrder, Long idCustomer, String reason);

    void publishOrderPaid(Order order, Long paymentId);

    void publishOrderConfirmed(Order order);

    void publishOrderDelivered(Order order, java.time.Instant deliveredAt);

    void publishOrderRefundRequested(Order order, Long paymentId, String reason);
}
