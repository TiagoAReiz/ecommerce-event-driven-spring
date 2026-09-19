package ecommerce_event_driven.order.modules.order.application.ports.inbound.messaging;

import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockRejectedEvent;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockReservedEvent;

/** Resposta do inventory a reserva pedida no OrderCreated. */
public interface StockEventsConsumerPort {

    void onStockReserved(StockReservedEvent event);

    void onStockRejected(StockRejectedEvent event);
}
