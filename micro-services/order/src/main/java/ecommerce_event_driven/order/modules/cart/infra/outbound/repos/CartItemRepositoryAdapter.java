package ecommerce_event_driven.order.modules.cart.infra.outbound.repos;

import ecommerce_event_driven.order.modules.cart.application.mappers.CartItemMapper;
import ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos.CartItemRepositoryPort;
import ecommerce_event_driven.order.modules.cart.domain.models.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CartItemRepositoryAdapter implements CartItemRepositoryPort {

    private final CartItemJpaRepository jpaRepository;

    public CartItemRepositoryAdapter(CartItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CartItem save(CartItem item) {
        return CartItemMapper.toDomain(jpaRepository.save(CartItemMapper.toEntity(item)));
    }

    @Override
    public Optional<CartItem> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(CartItemMapper::toDomain);
    }

    @Override
    public List<CartItem> findByIdCart(Long idCart) {
        return jpaRepository.findByCartIdAndDeletedAtIsNull(idCart).stream()
                .map(CartItemMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<CartItem> findByIdCartAndIdProduct(Long idCart, Long idProduct) {
        return jpaRepository.findByCartIdAndIdProductAndDeletedAtIsNull(idCart, idProduct)
                .map(CartItemMapper::toDomain);
    }
}
