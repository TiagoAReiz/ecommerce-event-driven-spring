package ecommerce_event_driven.shipment.modules.shipment.infra.inbound.dtos;

import java.math.BigDecimal;

/**
 * Evento order.confirmed recebido via Kafka.
 */
public record OrderConfirmedEventDto(
        String eventId,
        String producedAt,
        Long orderId,
        Long customerId,
        Long addressId,
        BigDecimal freightCost) {}
