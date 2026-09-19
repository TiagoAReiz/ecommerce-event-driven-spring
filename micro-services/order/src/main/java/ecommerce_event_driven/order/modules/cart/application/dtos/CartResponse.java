package ecommerce_event_driven.order.modules.cart.application.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Resposta do carrinho com todos os itens hidratados.
 */
public record CartResponse(
        List<CartItemResponse> items,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal itemsCost,
        Instant updatedAt
) {}
