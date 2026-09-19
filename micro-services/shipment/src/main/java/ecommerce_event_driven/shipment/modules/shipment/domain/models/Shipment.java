package ecommerce_event_driven.shipment.modules.shipment.domain.models;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Shipment(
        Long id,
        Long idOrder,
        Long idUser,
        /** Endereco do comprador no servico user. O snapshot de destino fica em destination. */
        Long idAddressUser,
        ShipmentStatus status,
        BigDecimal freightTax,
        String trackingCode,
        String cancelReason,
        AddressSnapshot destination,
        AddressSnapshot origin,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
