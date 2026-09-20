package ecommerce_event_driven.order.modules.order.infra.outbound.messaging;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events.*;
import ecommerce_event_driven.order.shared.outbox.OutboxMessage;
import ecommerce_event_driven.order.shared.outbox.OutboxWriter;

/**
 * Implementacao de OrderEventPublisherPort que grava eventos na outbox.
 * Todos os metodos publicam dentro da transacao que muda o status.
 */
@Component
public class OrderEventOutboxPublisher implements OrderEventPublisherPort {

    private final OutboxWriter outboxWriter;

    public OrderEventOutboxPublisher(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void publishOrderCreated(Order order) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        var items = order.items().stream()
                .map(item -> new OrderCreatedEvent.Item(item.idProduct(), item.quantity()))
                .toList();

        var event = new OrderCreatedEvent(
                eventId.toString(),
                now,
                order.id(),
                order.idCustomer(),
                order.totalCost(),
                items
        );

        var message = new OutboxMessage(
                eventId,
                "order",
                order.id().toString(),
                "ecommerce.order.created.v1",
                "orderCreated",
                event
        );

        outboxWriter.write(message);
    }

    @Override
    public void publishOrderCancelled(Long idOrder, Long idCustomer, String reason) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        var event = new OrderCancelledEvent(
                eventId.toString(),
                now,
                idOrder,
                idCustomer,
                reason
        );

        var message = new OutboxMessage(
                eventId,
                "order",
                idOrder.toString(),
                "ecommerce.order.cancelled.v1",
                "orderCancelled",
                event
        );

        outboxWriter.write(message);
    }

    @Override
    public void publishOrderPaid(Order order, Long paymentId) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        var event = new OrderPaidEvent(
                eventId.toString(),
                now,
                order.id(),
                order.idCustomer(),
                paymentId,
                order.items().stream()
                        .map(item -> new OrderPaidEvent.Item(item.idProduct(), item.quantity()))
                        .toList()
        );

        var message = new OutboxMessage(
                eventId,
                "order",
                order.id().toString(),
                "ecommerce.order.paid.v1",
                "orderPaid",
                event
        );

        outboxWriter.write(message);
    }

    @Override
    public void publishOrderConfirmed(Order order) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        var event = new OrderConfirmedEvent(
                eventId.toString(),
                now,
                order.id(),
                order.idCustomer(),
                order.idAddress(),
                order.freightCost()
        );

        var message = new OutboxMessage(
                eventId,
                "order",
                order.id().toString(),
                "ecommerce.order.confirmed.v1",
                "orderConfirmed",
                event
        );

        outboxWriter.write(message);
    }

    @Override
    public void publishOrderDelivered(Order order, Instant deliveredAt) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        var event = new OrderDeliveredEvent(
                eventId.toString(),
                now,
                order.id(),
                order.idCustomer(),
                deliveredAt,
                order.items().stream()
                        .map(item -> item.idProduct())
                        .distinct()
                        .map(OrderDeliveredEvent.Item::new)
                        .toList()
        );

        var message = new OutboxMessage(
                eventId,
                "order",
                order.id().toString(),
                "ecommerce.order.delivered.v1",
                "orderDelivered",
                event
        );

        outboxWriter.write(message);
    }

    @Override
    public void publishOrderRefundRequested(Order order, Long paymentId, String reason) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        var event = new OrderRefundRequestedEvent(
                eventId.toString(),
                now,
                order.id(),
                order.idCustomer(),
                paymentId,
                reason
        );

        var message = new OutboxMessage(
                eventId,
                "order",
                order.id().toString(),
                "ecommerce.order.refund.requested.v1",
                "orderRefundRequested",
                event
        );

        outboxWriter.write(message);
    }
}
