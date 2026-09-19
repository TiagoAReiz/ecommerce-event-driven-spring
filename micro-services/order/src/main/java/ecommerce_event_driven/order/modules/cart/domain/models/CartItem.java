package ecommerce_event_driven.order.modules.cart.domain.models;

import java.time.Instant;
import lombok.Builder;

/** Sem preco: o carrinho hidrata do inventory e revalida no checkout. */
@Builder(toBuilder = true)
public record CartItem(
        Long id,
        Long idCart,
        Long idProduct,
        Integer quantity,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
