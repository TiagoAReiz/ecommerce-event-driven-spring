package ecommerce_event_driven.order.modules.cart.application.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta de item do carrinho com hidratacao do inventory.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartItemResponse(
        Long id,
        Long idProduct,
        Integer quantity,
        ProductInfo product,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal lineTotal,
        List<String> issues
) {
    public record ProductInfo(
            Long id,
            String name,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal price,
            String photoUrl,
            Boolean available,
            Boolean active
    ) {}
}
