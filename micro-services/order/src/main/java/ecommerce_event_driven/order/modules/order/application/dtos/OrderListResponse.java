package ecommerce_event_driven.order.modules.order.application.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Resposta de listagem de pedidos.
 */
public record OrderListResponse(
        List<OrderSummary> content,
        PageMeta page
) {
    public record OrderSummary(
            Long id,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal itemsCost,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal freightCost,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal totalCost,
            Integer itemCount,
            FirstItem firstItem,
            Long idCustomer,  // Null em GET /orders, presente em GET /orders/manage
            Instant createdAt
    ) {}

    public record FirstItem(
            String productName,
            String productPhotoUrl
    ) {}

    public record PageMeta(
            Integer number,
            Integer size,
            Long totalElements,
            Integer totalPages
    ) {}
}
