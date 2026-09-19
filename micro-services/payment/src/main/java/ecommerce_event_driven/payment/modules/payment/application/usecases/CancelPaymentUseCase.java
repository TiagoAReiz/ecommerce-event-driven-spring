package ecommerce_event_driven.payment.modules.payment.application.usecases;

import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos.PaymentRepositoryPort;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.modules.payment.domain.models.PaymentStatus;
import ecommerce_event_driven.payment.shared.web.ConflictException;
import ecommerce_event_driven.payment.shared.web.NotFoundException;
import ecommerce_event_driven.payment.shared.web.UnprocessableException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelPaymentUseCase {
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;

    public CancelPaymentUseCase(PaymentRepositoryPort paymentRepository, PaymentGatewayPort paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public Payment execute(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado"));

        if (payment.status() == PaymentStatus.captured || payment.status() == PaymentStatus.authorized) {
            throw new ConflictException("Pagamento já foi capturado", "PAYMENT_ALREADY_CAPTURED");
        }

        if (payment.status() == PaymentStatus.failed || payment.status() == PaymentStatus.refunded
                || payment.status() == PaymentStatus.cancelled) {
            throw new UnprocessableException("Pagamento não pode ser cancelado", "INVALID_STATUS");
        }

        paymentGateway.cancel(payment.externalId());

        Payment updated = payment.toBuilder()
                .status(PaymentStatus.cancelled)
                .updatedAt(Instant.now())
                .build();
        return paymentRepository.save(updated);
    }
}
