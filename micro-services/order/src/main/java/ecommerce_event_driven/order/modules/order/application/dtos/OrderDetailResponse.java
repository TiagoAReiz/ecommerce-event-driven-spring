package ecommerce_event_driven.order.modules.order.application.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta de detalhe de pedido com projecoes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDetailResponse(
        Long id,
        String status,
        Long idAddress,
        List<ItemDetail> items,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal itemsCost,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal freightCost,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal totalCost,
        PaymentProjection payment,
        ShipmentProjection shipment,
        Instant createdAt,
        Instant updatedAt
) {
    public record ItemDetail(
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

    public record PaymentProjection(
            Long id,
            String status,
            String provider
    ) {}

    public record ShipmentProjection(
            Long id,
            String status,
            String trackingCode
    ) {}
}
