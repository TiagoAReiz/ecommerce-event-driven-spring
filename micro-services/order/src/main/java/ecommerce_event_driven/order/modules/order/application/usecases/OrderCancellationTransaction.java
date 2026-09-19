package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parte transacional do cancelamento. Classe propria porque o Spring so
 * intercepta @Transactional em chamada entre beans.
 */
@Component
class OrderCancellationTransaction {

    private final OrderRepositoryPort orders;

    OrderCancellationTransaction(OrderRepositoryPort orders) {
        this.orders = orders;
    }

    /**
     * So cancela quem ainda esta em pending: pedido pago sai por refunded, nao
     * por cancelamento de estoque. O proprio UPDATE faz esse teste.
     */
    @Transactional
    boolean cancelIfPending(Long idOrder) {
        return orders.updateStatus(idOrder, OrderStatus.pending, OrderStatus.cancelled);
    }
}
