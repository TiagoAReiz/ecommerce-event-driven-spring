package ecommerce_event_driven.shipment.modules.shipment.infra.outbound.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.events.ShipmentEventPublisherPort;
import ecommerce_event_driven.shipment.modules.shipment.domain.events.ShipmentStatusChangedEvent;
import ecommerce_event_driven.shipment.shared.outbox.OutboxMessage;
import ecommerce_event_driven.shipment.shared.outbox.OutboxWriter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Publicador de eventos de envio via outbox.
 * Os eventos sao gravados na tabela outbox e o Debezium publica depois do commit.
 */
@Component
public class ShipmentEventOutboxPublisher implements ShipmentEventPublisherPort {
    private final OutboxWriter outboxWriter;

    public ShipmentEventOutboxPublisher(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void publishStatusChanged(ShipmentStatusChangedEvent event) throws JsonProcessingException {
        OutboxMessage message = new OutboxMessage(
                UUID.randomUUID(),
                "shipment",
                String.valueOf(event.orderId()),
                "ecommerce.shipment.status.changed.v1",
                "shipmentStatusChanged",
                event);
        outboxWriter.write(message);
    }
}
