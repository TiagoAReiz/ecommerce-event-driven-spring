package ecommerce_event_driven.order.modules.cart.infra.outbound.repos;

import ecommerce_event_driven.order.modules.cart.application.mappers.CartMapper;
import ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos.CartRepositoryPort;
import ecommerce_event_driven.order.modules.cart.domain.models.Cart;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CartRepositoryAdapter implements CartRepositoryPort {

    private final CartJpaRepository jpaRepository;

    public CartRepositoryAdapter(CartJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Cart save(Cart cart) {
        return CartMapper.toDomain(jpaRepository.save(CartMapper.toEntity(cart)));
    }

    @Override
    public Optional<Cart> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(CartMapper::toDomain);
    }

    @Override
    public Optional<Cart> findByIdUser(Long idUser) {
        return jpaRepository.findByIdUserAndDeletedAtIsNull(idUser).map(CartMapper::toDomain);
    }
}
