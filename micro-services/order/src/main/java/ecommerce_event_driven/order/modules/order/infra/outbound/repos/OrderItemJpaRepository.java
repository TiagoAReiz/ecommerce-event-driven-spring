package ecommerce_event_driven.order.modules.order.infra.outbound.repos;

import ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity.OrderItemEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Uso interno do OrderRepositoryAdapter: item de pedido nao tem porta propria,
 * a raiz do agregado e o pedido.
 */
@Repository
public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, Long> {

    List<OrderItemEntity> findByOrderId(Long idOrder);

    /** Carrega os itens de uma pagina inteira de pedidos em uma query so. */
    List<OrderItemEntity> findByOrderIdIn(Collection<Long> idOrders);
}
