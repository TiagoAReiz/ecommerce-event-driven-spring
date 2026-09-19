package ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos;

import ecommerce_event_driven.order.modules.cart.domain.models.CartItem;
import java.util.List;
import java.util.Optional;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 */
public interface CartItemRepositoryPort {

    CartItem save(CartItem item);

    Optional<CartItem> findById(Long id);

    List<CartItem> findByIdCart(Long idCart);

    /** Usado no "adicionar ao carrinho": se ja existe, soma a quantidade. */
    Optional<CartItem> findByIdCartAndIdProduct(Long idCart, Long idProduct);
}
