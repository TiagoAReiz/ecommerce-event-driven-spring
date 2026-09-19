package ecommerce_event_driven.payment.modules.payment.application.usecases;

import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentRefundedEvent;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos.PaymentRepositoryPort;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.modules.payment.domain.models.PaymentStatus;
import ecommerce_event_driven.payment.modules.payment.infra.outbound.events.PaymentEventOutboxPublisher;
import ecommerce_event_driven.payment.shared.web.ConflictException;
import ecommerce_event_driven.payment.shared.web.NotFoundException;
import ecommerce_event_driven.payment.shared.web.UnprocessableException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundPaymentUseCase {
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final PaymentEventOutboxPublisher eventPublisher;

    public RefundPaymentUseCase(
            PaymentRepositoryPort paymentRepository,
            PaymentGatewayPort paymentGateway,
            PaymentEventOutboxPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Payment refundManual(Long paymentId, BigDecimal amount, String idempotencyKey) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado"));

        if (payment.status() != PaymentStatus.captured && payment.status() != PaymentStatus.authorized) {
            throw new UnprocessableException("Pagamento não pode ser estornado", "PAYMENT_NOT_REFUNDABLE");
        }

        if (payment.refundedAmount().add(amount).compareTo(payment.value()) > 0) {
            throw new ConflictException("Saldo insuficiente para estorno", "INSUFFICIENT_BALANCE_FOR_REFUND");
        }

        paymentGateway.refund(payment.externalId(), amount, idempotencyKey);

        BigDecimal newRefundedAmount = payment.refundedAmount().add(amount);
        boolean isFull = newRefundedAmount.compareTo(payment.value()) == 0;

        Payment updated = payment.toBuilder()
                .refundedAmount(newRefundedAmount)
                .status(isFull ? PaymentStatus.refunded : payment.status())
                .updatedAt(Instant.now())
                .build();
        Payment result = paymentRepository.save(updated);

        if (isFull) {
            PaymentRefundedEvent event = new PaymentRefundedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    payment.idOrder(),
                    paymentId,
                    amount,
                    newRefundedAmount,
                    true,
                    "manual",
                    Instant.now());
            eventPublisher.publishPaymentRefunded(event);
        }

        return result;
    }
}
