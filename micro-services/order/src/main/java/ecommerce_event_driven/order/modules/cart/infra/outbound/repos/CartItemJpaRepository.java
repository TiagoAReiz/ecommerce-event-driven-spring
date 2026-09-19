package ecommerce_event_driven.order.modules.cart.infra.outbound.repos;

import ecommerce_event_driven.order.modules.cart.infra.outbound.repos.entity.CartItemEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemJpaRepository extends JpaRepository<CartItemEntity, Long> {

    List<CartItemEntity> findByCartIdAndDeletedAtIsNull(Long idCart);

    /** Casa com cart_items_cart_product_uk: usado no "adicionar ou somar quantidade". */
    Optional<CartItemEntity> findByCartIdAndIdProductAndDeletedAtIsNull(Long idCart, Long idProduct);

    Optional<CartItemEntity> findByIdAndDeletedAtIsNull(Long id);
}
