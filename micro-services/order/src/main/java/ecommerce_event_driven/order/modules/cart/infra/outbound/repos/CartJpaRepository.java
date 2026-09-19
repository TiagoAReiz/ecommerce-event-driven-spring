package ecommerce_event_driven.order.modules.cart.infra.outbound.repos;

import ecommerce_event_driven.order.modules.cart.infra.outbound.repos.entity.CartEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartJpaRepository extends JpaRepository<CartEntity, Long> {

    /** Casa com cart_user_uk: no maximo um carrinho ativo por usuario. */
    Optional<CartEntity> findByIdUserAndDeletedAtIsNull(Long idUser);

    Optional<CartEntity> findByIdAndDeletedAtIsNull(Long id);
}
