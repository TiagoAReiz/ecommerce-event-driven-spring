package ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging;

import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.messaging.StockEventPublisherPort;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging.events.StockRejectedEvent;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.messaging.events.StockReservedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockEventKafkaPublisher implements StockEventPublisherPort {

    public static final String RESERVED_TOPIC = "ecommerce.stock.reserved.v1";
    public static final String REJECTED_TOPIC = "ecommerce.stock.rejected.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StockEventKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishStockReserved(Long idOrder) {
        StockReservedEvent event = new StockReservedEvent(
                UUID.randomUUID().toString(), Instant.now(), idOrder);
        kafkaTemplate.send(RESERVED_TOPIC, String.valueOf(idOrder), event);
    }

    @Override
    public void publishStockRejected(Long idOrder, Long idProduct) {
        StockRejectedEvent event = new StockRejectedEvent(
                UUID.randomUUID().toString(), Instant.now(), idOrder, idProduct);
        kafkaTemplate.send(REJECTED_TOPIC, String.valueOf(idOrder), event);
    }
}
