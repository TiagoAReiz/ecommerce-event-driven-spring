package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trata o evento stock.commit.failed: transicao paid -> cancelled
 * e publica order.cancelled + order.refund.requested dentro da mesma transacao.
 */
@Component
public class StockCommitFailedEventHandler {
    private static final Logger log = LoggerFactory.getLogger(StockCommitFailedEventHandler.class);

    private final OrderRepositoryPort orders;
    private final OrderEventPublisherPort publisher;

    public StockCommitFailedEventHandler(OrderRepositoryPort orders, OrderEventPublisherPort publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public void handle(Long orderId, Long productId, String reason) throws InvalidEventException {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));

        // Decidir pelo estado atual: so avanca se estiver em paid
        if (order.status() != OrderStatus.paid) {
            if (order.status().ordinal() > OrderStatus.paid.ordinal()) {
                // Estado a frente: ignorar (ja foi cancelado por outro caminho)
                log.debug("stock.commit.failed ignorado: pedido {} ja em estado {}", orderId, order.status());
            }
            return;
        }

        // Transicionar paid -> cancelled
        String cancelReason = "estoque indisponivel para o produto " + productId + ": " + reason;
        boolean updated = orders.updateStatus(orderId, OrderStatus.paid, OrderStatus.cancelled);
        if (!updated) {
            // Outro evento alterou o status: tratar como concorrencia normal
            log.debug("stock.commit.failed nao atualizou pedido {}: ja em estado diferente de paid", orderId);
            return;
        }

        // Recarregar para ter os dados completos
        Order updated_order = orders.findById(orderId)
                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado apos atualizacao"));

        // Publicar order.cancelled e order.refund.requested
        publisher.publishOrderCancelled(orderId, updated_order.idCustomer(), cancelReason);
        if (updated_order.paymentId() != null) {
            publisher.publishOrderRefundRequested(updated_order, updated_order.paymentId(), cancelReason);
        }

        log.info("Pedido {} cancelado por stock.commit.failed", orderId);
    }
}
