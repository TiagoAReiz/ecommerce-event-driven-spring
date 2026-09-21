package ecommerce_event_driven.order.modules.cart.application.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do PUT /cart/items/{idProduct}: so a quantidade.
 *
 * <p>Reaproveitar o corpo do POST obrigava a mandar o idProduct duas vezes, no
 * caminho e no corpo, e recusava com 400 quem seguisse o contrato.
 */
public record UpdateCartItemRequest(
        @NotNull(message = "quantity e obrigatoria")
        @Min(value = 1, message = "quantity deve ser >= 1")
        Integer quantity
) {}
