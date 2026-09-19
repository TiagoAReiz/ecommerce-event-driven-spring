package ecommerce_event_driven.shipment.modules.shipment.domain.events;

import java.time.Instant;

/**
 * Evento publicado quando o status de um envio muda.
 * Enviado via outbox em toda transicao (inclusive criacao).
 */
public record ShipmentStatusChangedEvent(
        Long shipmentId,
        Long orderId,
        String from,
        String to,
        String trackingCode,
        String reason,
        Instant changedAt) {}
