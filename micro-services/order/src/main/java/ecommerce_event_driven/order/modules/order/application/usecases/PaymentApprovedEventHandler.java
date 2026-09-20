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
 * Trata o evento payment.approved:
 * - Se pending e amount == total_cost: transiciona para paid, grava paymentId e payment_status=captured
 * - Se cancelled: publica order.refund.requested
 * - Se ja pago por outro paymentId: publica order.refund.requested
 * - Se ja pago por mesmo paymentId: ignora
 * - Se amount != total_cost: InvalidEventException -> DLT
 */
@Component
public class PaymentApprovedEventHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentApprovedEventHandler.class);

    private final OrderRepositoryPort orders;
    private final OrderEventPublisherPort publisher;

    public PaymentApprovedEventHandler(OrderRepositoryPort orders, OrderEventPublisherPort publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public void handle(Long orderId, Long paymentId, BigDecimal amount) throws InvalidEventException {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));

        // Validar amount == total_cost
        // compareTo, nao equals: BigDecimal.equals compara a escala, e 724.80 nao e igual a 724.8
        if (amount.compareTo(order.totalCost()) != 0) {
            throw new InvalidEventException("Valor do pagamento " + amount + " diverge do total do pedido " + order.totalCost());
        }

        // Decidir pelo estado atual
        if (order.status() == OrderStatus.pending) {
            // Transicionar pending -> paid
            boolean updated = orders.updateStatus(orderId, OrderStatus.pending, OrderStatus.paid);
            if (!updated) {
                // Outro evento alterou: tratar como concorrencia
                log.debug("payment.approved nao atualizou pedido {}: ja nao esta em pending", orderId);
                return;
            }

            // Recarregar e atualizar com os dados de pagamento
            Order updated_order = orders.findById(orderId)
                    .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado apos atualizacao"));

            Order with_payment = updated_order.toBuilder()
                    .paymentId(paymentId)
                    .paymentStatus("captured")
                    .build();
            orders.save(with_payment);

            // Publicar order.paid
            publisher.publishOrderPaid(with_payment, paymentId);

            log.info("Pedido {} avancou de pending para paid com paymentId {}", orderId, paymentId);

        } else if (order.status() == OrderStatus.cancelled) {
            // Pedido ja foi cancelado: publicar order.refund.requested
            publisher.publishOrderRefundRequested(order, paymentId, "pedido ja estava cancelado");
            log.info("payment.approved para pedido {} ja cancelado: publicando refund", orderId);

        } else if (order.paymentId() != null) {
            // Pedido ja foi pago por outro ou mesmo pagamento
            if (order.paymentId().equals(paymentId)) {
                // Mesmo paymentId: idempotencia, nao fazer nada
                log.debug("payment.approved duplicado para pedido {} com mesmo paymentId {}", orderId, paymentId);
            } else {
                // Outro paymentId: publicar refund para este
                publisher.publishOrderRefundRequested(order, paymentId, "pedido ja foi pago por outro pagamento");
                log.info("payment.approved para pedido {} com paymentId diferente: publicando refund para {}", orderId, paymentId);
            }
        }
    }
}
