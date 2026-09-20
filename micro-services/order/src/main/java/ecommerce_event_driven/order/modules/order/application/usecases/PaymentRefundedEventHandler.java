package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trata o evento payment.refunded:
 * - Se full: true e pedido em cancelled/shipped/delivered -> refunded
 * - Se full: true e pedido em paid/processing -> cancelled (publica order.cancelled) -> refunded
 * - Se full: false -> atualiza refunded_amount, sem mudar status
 * - Se ja refunded -> ignora
 */
@Component
public class PaymentRefundedEventHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentRefundedEventHandler.class);

    private final OrderRepositoryPort orders;
    private final OrderEventPublisherPort publisher;

    public PaymentRefundedEventHandler(OrderRepositoryPort orders, OrderEventPublisherPort publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public void handle(Long orderId, BigDecimal totalRefunded, boolean full) throws InvalidEventException {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));

        // Se ja refunded, ignorar
        if (order.status() == OrderStatus.refunded) {
            log.debug("payment.refunded ignorado para pedido {} ja em estado refunded", orderId);
            return;
        }

        // Estorno parcial: so atualizar projecao
        if (!full) {
            BigDecimal current = order.refundedAmount() != null ? order.refundedAmount() : BigDecimal.ZERO;
            orders.updatePaymentProjection(orderId, null, current.add(totalRefunded));
            log.info("Projecao refunded_amount atualizada para pedido {}: +{}", orderId, totalRefunded);
            return;
        }

        // Estorno total
        if (order.status() == OrderStatus.cancelled || order.status() == OrderStatus.shipped || order.status() == OrderStatus.delivered) {
            // Transicionar direto para refunded
            boolean updated = orders.updateStatus(orderId, order.status(), OrderStatus.refunded);
            if (!updated) {
                log.debug("payment.refunded nao atualizou pedido {}: estado mudou entre leitura e escrita", orderId);
                return;
            }

            // A projecao tem que acompanhar: sem isso o pedido aparece refunded com o
            // pagamento ainda "captured" na resposta da API.
            orders.updatePaymentProjection(orderId, "refunded", totalRefunded);

            log.info("Pedido {} transicionou para refunded com amount {}", orderId, totalRefunded);

        } else if (order.status() == OrderStatus.paid || order.status() == OrderStatus.processing) {
            // Primeiro cancelar
            boolean cancelled_updated = orders.updateStatus(orderId, order.status(), OrderStatus.cancelled);
            if (!cancelled_updated) {
                log.debug("payment.refunded nao cancelou pedido {}: estado mudou entre leitura e escrita", orderId);
                return;
            }

            Order cancelled_order = orders.findById(orderId)
                    .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado apos cancelamento"));

            // Publicar order.cancelled
            publisher.publishOrderCancelled(orderId, cancelled_order.idCustomer(), "estorno de pagamento");

            // Depois transicionar para refunded
            boolean refunded_updated = orders.updateStatus(orderId, OrderStatus.cancelled, OrderStatus.refunded);
            if (!refunded_updated) {
                log.debug("payment.refunded nao transicionou pedido {} de cancelled para refunded", orderId);
                return;
            }

            orders.updatePaymentProjection(orderId, "refunded", totalRefunded);

            log.info("Pedido {} transicionou paid/processing -> cancelled -> refunded com amount {}", orderId, totalRefunded);
        }
    }
}
