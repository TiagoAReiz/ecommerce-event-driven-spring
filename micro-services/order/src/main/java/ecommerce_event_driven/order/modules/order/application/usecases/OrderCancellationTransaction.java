package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import ecommerce_event_driven.order.shared.web.ConflictException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parte transacional do cancelamento, com publicacao de eventos dentro da transacao.
 * Classe propria porque o Spring so intercepta @Transactional em chamada entre beans.
 */
@Component
public class OrderCancellationTransaction {

    private final OrderRepositoryPort orders;
    private final OrderEventPublisherPort publisher;

    public OrderCancellationTransaction(OrderRepositoryPort orders, OrderEventPublisherPort publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    /**
     * Cancela um pedido validando a maquina de estados.
     * Publica order.cancelled e, se aplicavel, order.refund.requested dentro da mesma transacao.
     *
     * Validacoes:
     * - pending -> cancelled: sem refund
     * - paid/processing -> cancelled: com refund
     * - processing so pode ser cancelado pelo owner (validacao fora)
     */
    @Transactional
    public void cancel(Long idOrder, Long idCustomer, String reason, boolean isOwner) {
        Optional<Order> orderOpt = orders.findById(idOrder);
        if (orderOpt.isEmpty()) {
            throw new ConflictException("ORDER_NOT_FOUND", "Pedido nao encontrado");
        }

        Order order = orderOpt.get();

        // Validar que e do cliente ou que o cliente e owner
        if (!order.idCustomer().equals(idCustomer)) {
            throw new ConflictException("UNAUTHORIZED", "Nao autorizado");
        }

        // Validar transicao
        if (!canCancel(order.status(), isOwner)) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "Status nao permite cancelamento");
        }

        // Transicionar para cancelled
        boolean updated = orders.updateStatus(idOrder, order.status(), OrderStatus.cancelled);
        if (!updated) {
            // Alguem atualizou o status entre a leitura e a escrita
            throw new ConflictException("ORDER_ALREADY_CANCELLED", "Pedido foi alterado");
        }

        // Recarregar o pedido para ter os dados atualizados
        Order updated_order = orders.findById(idOrder)
                .orElseThrow(() -> new ConflictException("ORDER_NOT_FOUND", "Pedido nao encontrado"));

        // Publicar order.cancelled
        publisher.publishOrderCancelled(idOrder, idCustomer, reason);

        // Se havia pagamento, publicar order.refund.requested
        if (updated_order.paymentId() != null &&
            (order.status() == OrderStatus.paid || order.status() == OrderStatus.processing)) {
            publisher.publishOrderRefundRequested(updated_order, updated_order.paymentId(), reason);
        }
    }

    /**
     * So cancela quem ainda esta em pending: pedido pago sai por refunded, nao
     * por cancelamento de estoque. Processing so pode ser cancelado por owner.
     */
    private boolean canCancel(OrderStatus status, boolean isOwner) {
        return switch (status) {
            case pending, paid -> true;
            case processing -> isOwner;
            default -> false;
        };
    }
}
