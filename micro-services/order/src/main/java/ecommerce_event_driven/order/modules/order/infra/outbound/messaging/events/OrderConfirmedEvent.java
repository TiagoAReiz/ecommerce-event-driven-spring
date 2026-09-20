package ecommerce_event_driven.order.modules.order.infra.outbound.messaging.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pedido pago e com estoque baixado. Unico gatilho de criacao de envio.
 *
 * <p>addressId e freightCost viajam no evento porque o shipment monta o destino e copia
 * o frete sem recalcular nem consultar o order.
 */
public record OrderConfirmedEvent(
        String eventId,
        Instant producedAt,
        Long orderId,
        Long customerId,
        Long addressId,
        BigDecimal freightCost
) {}
