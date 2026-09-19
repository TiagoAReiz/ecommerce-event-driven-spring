package ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging;

import ecommerce_event_driven.inventory.config.InvalidEventException;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.messaging.OrderCreatedConsumerPort;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.ReserveStockUseCase;
import java.util.List;

import ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedConsumer implements OrderCreatedConsumerPort {

    private final ReserveStockUseCase reserveStock;

    public OrderCreatedConsumer(ReserveStockUseCase reserveStock) {
        this.reserveStock = reserveStock;
    }

    /** Traduz o evento para o comando do caso de uso e entrega. */
    @Override
    @KafkaListener(topics = "ecommerce.order.created.v1", groupId = "inventory")
    public void onorderCreated(OrderCreatedEvent orderCreatedEvent) {
        // Validacoes: items vazio ou quantity <= 0 lancam InvalidEventException
        // que faz o evento ir direto para DLT sem retry
        if (orderCreatedEvent.items() == null || orderCreatedEvent.items().isEmpty()) {
            throw new InvalidEventException("order.created event tem items vazio");
        }

        for (var item : orderCreatedEvent.items()) {
            if (item.quantity() <= 0) {
                throw new InvalidEventException("order.created event tem quantity <= 0 para produto " + item.productId());
            }
        }

        List<ReserveStockUseCase.Item> items = orderCreatedEvent.items().stream()
                .map(item -> new ReserveStockUseCase.Item(item.productId(), item.quantity()))
                .toList();

        reserveStock.execute(orderCreatedEvent.orderId(), items);
    }
}
