package ecommerce_event_driven.payment.modules.payment.infra.inbound.messaging;

import ecommerce_event_driven.payment.config.InvalidEventException;
import ecommerce_event_driven.payment.modules.payment.application.dtos.OrderRefundRequestedEvent;
import ecommerce_event_driven.payment.modules.payment.application.usecases.RefundPaymentUseCase;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.modules.payment.domain.models.PaymentStatus;
import ecommerce_event_driven.payment.modules.payment.infra.outbound.repos.PaymentRepositoryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderRefundRequestedConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderRefundRequestedConsumer.class);
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final PaymentRepositoryAdapter paymentRepository;

    public OrderRefundRequestedConsumer(
            RefundPaymentUseCase refundPaymentUseCase,
            PaymentRepositoryAdapter paymentRepository) {
        this.refundPaymentUseCase = refundPaymentUseCase;
        this.paymentRepository = paymentRepository;
    }

    @KafkaListener(
            topics = "ecommerce.order.refund.requested.v1",
            groupId = "payment")
    public void onOrderRefundRequested(OrderRefundRequestedEvent event) {
        if (event == null || event.paymentId() == null) {
            throw new InvalidEventException("Evento inválido: paymentId ausente");
        }

        Payment payment = paymentRepository.findById(event.paymentId())
                .orElse(null);

        if (payment == null) {
            log.warn("Pagamento {} não encontrado para refund automático", event.paymentId());
            return;
        }

        if (payment.status() == PaymentStatus.refunded) {
            log.warn("Pagamento {} já foi refundido", event.paymentId());
            return;
        }

        if (payment.status() != PaymentStatus.captured && payment.status() != PaymentStatus.authorized) {
            log.warn("Pagamento {} não pode ser refundido (status: {})", event.paymentId(), payment.status());
            return;
        }

        try {
            String idempotencyKey = "refund-" + event.paymentId();
            refundPaymentUseCase.refundManual(event.paymentId(), payment.value(), idempotencyKey);
            log.info("Refund automático processado para pagamento {}", event.paymentId());
        } catch (Exception e) {
            log.error("Erro ao processar refund automático para pagamento {}", event.paymentId(), e);
            throw e;
        }
    }
}
