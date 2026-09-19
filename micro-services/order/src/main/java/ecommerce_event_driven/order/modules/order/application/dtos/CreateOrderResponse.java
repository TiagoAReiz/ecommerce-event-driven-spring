package ecommerce_event_driven.order.modules.order.application.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response para criacao de pedido.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateOrderResponse(
        Long id,
        String status,
        Long idAddress,
        List<OrderItemResponse> items,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal itemsCost,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal freightCost,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalCost,
        String stockReservation,
        NextStep nextStep,
        Instant createdAt
) {
    public record OrderItemResponse(
            Long id,
            Long idProduct,
            String productName,
            String productPhotoUrl,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal priceAtTime,
            Integer quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal lineTotal
    ) {}

    public record NextStep(
            String action,
            String href
    ) {}
}
