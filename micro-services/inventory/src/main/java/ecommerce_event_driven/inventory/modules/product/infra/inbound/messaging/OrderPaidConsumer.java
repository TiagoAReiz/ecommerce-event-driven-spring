package ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging;

import ecommerce_event_driven.inventory.config.InvalidEventException;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.CommitStockUseCase;
import ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderPaidEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaidConsumer {

    private final CommitStockUseCase commitStock;

    public OrderPaidConsumer(CommitStockUseCase commitStock) {
        this.commitStock = commitStock;
    }

    @KafkaListener(topics = "ecommerce.order.paid.v1", groupId = "inventory")
    public void onOrderPaid(OrderPaidEvent event) {
        if (event == null || event.orderId() == null || event.customerId() == null || event.items() == null || event.items().isEmpty()) {
            throw new InvalidEventException("OrderPaidEvent invalido");
        }

        var items = event.items().stream()
                .map(item -> new CommitStockUseCase.Item(item.productId(), item.quantity()))
                .toList();

        commitStock.execute(event.orderId(), items);
    }
}
