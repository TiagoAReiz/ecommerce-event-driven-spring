package ecommerce_event_driven.order.modules.order.infra.inbound.messaging;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.order.application.usecases.PaymentApprovedEventHandler;
import ecommerce_event_driven.order.modules.order.application.usecases.PaymentFailedEventHandler;
import ecommerce_event_driven.order.modules.order.application.usecases.PaymentRefundedEventHandler;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.PaymentApprovedEvent;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.PaymentFailedEvent;
import ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.PaymentRefundedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de eventos de pagamento.
 * Trata payment.approved, payment.failed e payment.refunded.
 */
@Component
public class PaymentEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsConsumer.class);

    private final PaymentApprovedEventHandler paymentApprovedHandler;
    private final PaymentFailedEventHandler paymentFailedHandler;
    private final PaymentRefundedEventHandler paymentRefundedHandler;

    public PaymentEventsConsumer(
            PaymentApprovedEventHandler paymentApprovedHandler,
            PaymentFailedEventHandler paymentFailedHandler,
            PaymentRefundedEventHandler paymentRefundedHandler) {
        this.paymentApprovedHandler = paymentApprovedHandler;
        this.paymentFailedHandler = paymentFailedHandler;
        this.paymentRefundedHandler = paymentRefundedHandler;
    }

    /**
     * Pagamento foi aprovado.
     * Se pending e valor correto: transiciona para paid.
     * Se cancelled ou ja pago por outro: publica refund.
     * Se ja pago pelo mesmo: ignora.
     */
    @KafkaListener(topics = "ecommerce.payment.approved.v1", groupId = "order")
    public void onPaymentApproved(PaymentApprovedEvent event) throws InvalidEventException {
        log.debug("payment.approved recebido para pedido {} com paymentId {}", event.orderId(), event.paymentId());
        paymentApprovedHandler.handle(event.orderId(), event.paymentId(), event.amount());
    }

    /**
     * Pagamento foi recusado.
     * Atualiza projecao payment_status sem mudar o status do pedido.
     */
    @KafkaListener(topics = "ecommerce.payment.failed.v1", groupId = "order")
    public void onPaymentFailed(PaymentFailedEvent event) throws InvalidEventException {
        log.debug("payment.failed recebido para pedido {} com status {}", event.orderId(), event.status());
        paymentFailedHandler.handle(event.orderId(), event.status(), event.statusDetail());
    }

    /**
     * Pagamento foi estornado.
     * Se full e em terminal: transiciona para refunded.
     * Se full e em paid/processing: cancela primeiro, depois refunda.
     * Se parcial: atualiza refunded_amount.
     */
    @KafkaListener(topics = "ecommerce.payment.refunded.v1", groupId = "order")
    public void onPaymentRefunded(PaymentRefundedEvent event) throws InvalidEventException {
        log.debug("payment.refunded recebido para pedido {} full={}", event.orderId(), event.full());
        paymentRefundedHandler.handle(event.orderId(), event.totalRefunded(), event.full());
    }
}
