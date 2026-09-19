package ecommerce_event_driven.order.modules.cart.application.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

/**
 * Request para adicionar ou atualizar item no carrinho.
 */
public record CartItemRequest(
        @NotNull(message = "idProduct e obrigatorio")
        Long idProduct,
        @NotNull(message = "quantity e obrigatoria")
        @Min(value = 1, message = "quantity deve ser >= 1")
        Integer quantity
) {}
