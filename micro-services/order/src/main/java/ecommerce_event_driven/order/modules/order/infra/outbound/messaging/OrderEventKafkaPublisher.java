package ecommerce_event_driven.order.modules.order.infra.outbound.messaging;

import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events.OrderCancelledEvent;
import ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events.OrderCreatedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventKafkaPublisher implements OrderEventPublisherPort {

    public static final String CREATED_TOPIC = "ecommerce.order.created.v1";
    public static final String CANCELLED_TOPIC = "ecommerce.order.cancelled.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                order.id(),
                order.idCustomer(),
                order.totalCost(),
                order.items().stream()
                        .map(item -> new OrderCreatedEvent.Item(item.idProduct(), item.quantity()))
                        .toList());

        kafkaTemplate.send(CREATED_TOPIC, String.valueOf(order.id()), event);
    }

    @Override
    public void publishOrderCancelled(Long idOrder, Long idCustomer, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                idOrder,
                idCustomer,
                reason);

        kafkaTemplate.send(CANCELLED_TOPIC, String.valueOf(idOrder), event);
    }
}
