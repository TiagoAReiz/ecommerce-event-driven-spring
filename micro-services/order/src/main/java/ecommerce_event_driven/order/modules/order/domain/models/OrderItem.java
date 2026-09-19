package ecommerce_event_driven.order.modules.order.domain.models;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

/**
 * Item de pedido: escrito uma vez, junto do pedido, e nunca mais alterado. Nao
 * tem deletedAt nem idOrder: ele so existe dentro do {@link Order} que o contem.
 */
@Builder(toBuilder = true)
public record OrderItem(
        Long id,
        Long idProduct,
        /** [snapshot] */
        String productName,
        /** [snapshot] */
        String productPhotoUrl,
        BigDecimal priceAtTime,
        Integer quantity,
        Instant createdAt,
        Instant updatedAt) {
}
