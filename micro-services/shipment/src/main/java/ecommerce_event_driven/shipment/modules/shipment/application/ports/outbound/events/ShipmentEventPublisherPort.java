package ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import ecommerce_event_driven.shipment.modules.shipment.domain.events.ShipmentStatusChangedEvent;

/**
 * Port para publicacao de eventos de envio.
 */
public interface ShipmentEventPublisherPort {
    void publishStatusChanged(ShipmentStatusChangedEvent event) throws JsonProcessingException;
}
