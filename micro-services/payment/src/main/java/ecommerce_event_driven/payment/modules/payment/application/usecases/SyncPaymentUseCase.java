package ecommerce_event_driven.payment.modules.payment.application.usecases;

import ecommerce_event_driven.payment.modules.payment.application.mappers.MercadoPagoStatusMapper;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos.PaymentRepositoryPort;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.modules.payment.domain.models.PaymentStatus;
import ecommerce_event_driven.payment.shared.web.ConflictException;
import ecommerce_event_driven.payment.shared.web.NotFoundException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class SyncPaymentUseCase {
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;

    public SyncPaymentUseCase(PaymentRepositoryPort paymentRepository, PaymentGatewayPort paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public SyncResponse execute(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado"));

        if (payment.externalId() == null) {
            throw new NotFoundException("Pagamento sem external_id");
        }

        JsonNode mpPayment = paymentGateway.getPayment(payment.externalId());
        String mpStatus = mpPayment.get("status").asText();
        PaymentStatus remoteStatus = MercadoPagoStatusMapper.mapMercadoPagoStatus(mpStatus);

        if (statusOrderOf(remoteStatus) < statusOrderOf(payment.status())) {
            throw new ConflictException("Status local à frente do remoto", "STATUS_REGRESSION_PREVENTED");
        }

        boolean changed = payment.status() != remoteStatus;
        if (changed) {
            Payment updated = payment.toBuilder()
                    .status(remoteStatus)
                    .statusDetail(mpPayment.has("status_detail") ? mpPayment.get("status_detail").asText() : null)
                    .updatedAt(Instant.now())
                    .build();
            paymentRepository.save(updated);
        }

        return new SyncResponse(changed);
    }

    public record SyncResponse(boolean changed) {}

    private int statusOrderOf(PaymentStatus status) {
        return switch (status) {
            case pending -> 0;
            case authorized -> 1;
            case captured -> 2;
            case failed -> -1;
            case refunded -> 3;
            case cancelled -> -1;
        };
    }
}
