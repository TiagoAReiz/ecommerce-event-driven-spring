package ecommerce_event_driven.order.modules.order.application.dtos;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Request para criar pedido.
 */
public record CreateOrderRequest(
        @NotNull(message = "addressId e obrigatorio")
        Long addressId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal expectedTotalCost
) {}
