package ecommerce_event_driven.order.modules.order.infra.inbound.messaging;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.order.application.usecases.ShipmentStatusChangedEventHandler;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.ShipmentStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de eventos de envio.
 * Trata shipment.status.changed: atualiza projecoes e transiciona status conforme necessario.
 */
@Component
public class ShipmentEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventsConsumer.class);

    private final ShipmentStatusChangedEventHandler shipmentHandler;

    public ShipmentEventsConsumer(ShipmentStatusChangedEventHandler shipmentHandler) {
        this.shipmentHandler = shipmentHandler;
    }

    /**
     * Qualquer transicao de status de envio.
     * Atualiza projecoes e transiciona o pedido conforme o novo status.
     */
    @KafkaListener(topics = "ecommerce.shipment.status.changed.v1", groupId = "order")
    public void onShipmentStatusChanged(ShipmentStatusChangedEvent event) throws InvalidEventException {
        log.debug("shipment.status.changed recebido: pedido {} shipment {} {} -> {}",
                event.orderId(), event.shipmentId(), event.from(), event.to());
        shipmentHandler.handle(event.orderId(), event.shipmentId(), event.from(), event.to(),
                event.trackingCode(), event.changedAt());
    }
}
