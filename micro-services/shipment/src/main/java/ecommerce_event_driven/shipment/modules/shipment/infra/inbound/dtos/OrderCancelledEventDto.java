package ecommerce_event_driven.shipment.modules.shipment.infra.inbound.dtos;

/**
 * Evento order.cancelled recebido via Kafka.
 */
public record OrderCancelledEventDto(
        String eventId,
        String producedAt,
        Long orderId,
        Long customerId,
        String reason) {}
