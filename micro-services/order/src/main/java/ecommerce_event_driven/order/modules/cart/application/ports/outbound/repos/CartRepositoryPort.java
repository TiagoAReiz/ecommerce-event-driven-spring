package ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos;

import ecommerce_event_driven.order.modules.cart.domain.models.Cart;
import java.util.Optional;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 */
public interface CartRepositoryPort {

    Cart save(Cart cart);

    Optional<Cart> findById(Long id);

    Optional<Cart> findByIdUser(Long idUser);
}
