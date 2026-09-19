package ecommerce_event_driven.order.modules.order.domain.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * Pedido e historico: nao existe deleted_at, so cancelamento via status.
 *
 * <p>Raiz do agregado. Os itens vem junto porque o invariante do dinheiro
 * atravessa os dois: {@code itemsCost} e a soma de {@code priceAtTime *
 * quantity} dos itens, e {@code totalCost} e {@code itemsCost + freightCost}.
 * Salvar item sem passar pelo pedido deixaria esses totais errados.
 */
@Builder(toBuilder = true)
public record Order(
        Long id,
        Long idCustomer,
        /** Endereco de entrega no servico user. O snapshot completo fica no shipment. */
        Long idAddress,
        OrderStatus status,
        List<OrderItem> items,
        BigDecimal itemsCost,
        BigDecimal freightCost,
        BigDecimal totalCost,
        Instant createdAt,
        Instant updatedAt) {

    public Order {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
