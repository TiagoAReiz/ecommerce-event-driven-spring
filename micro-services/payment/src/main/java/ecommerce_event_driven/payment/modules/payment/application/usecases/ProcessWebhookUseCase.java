package ecommerce_event_driven.payment.modules.payment.application.usecases;

import ecommerce_event_driven.payment.config.InvalidEventException;
import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentApprovedEvent;
import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentFailedEvent;
import ecommerce_event_driven.payment.modules.payment.application.mappers.MercadoPagoStatusMapper;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos.PaymentRepositoryPort;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.modules.payment.domain.models.PaymentStatus;
import ecommerce_event_driven.payment.modules.payment.infra.outbound.events.PaymentEventOutboxPublisher;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class ProcessWebhookUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessWebhookUseCase.class);
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final PaymentEventOutboxPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;

    public ProcessWebhookUseCase(
            PaymentRepositoryPort paymentRepository,
            PaymentGatewayPort paymentGateway,
            PaymentEventOutboxPublisher eventPublisher,
            StringRedisTemplate redisTemplate) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void execute(JsonNode payload) {
        String type = payload.get("type").asText();
        if (!"payment".equals(type)) {
            throw new InvalidEventException("Webhook type não é payment");
        }

        String mpEventId = payload.get("id").asText();
        String redisKey = "payment:webhook:" + mpEventId;

        // Dedupe em Redis
        Boolean alreadyProcessed = redisTemplate.hasKey(redisKey);
        if (alreadyProcessed != null && alreadyProcessed) {
            log.info("Webhook duplicado: {}", mpEventId);
            return;
        }

        String externalPaymentId = payload.get("data").get("id").asText();

        // Busca no MP
        JsonNode mpPayment = paymentGateway.getPayment(externalPaymentId);
        String externalRef = mpPayment.get("external_reference").asText();

        // Acha o pagamento local
        Payment payment = paymentRepository.findByExternalId(externalPaymentId)
                .orElse(null);

        if (payment == null) {
            log.warn("Webhook órfão: external_id {}", externalPaymentId);
            redisTemplate.opsForValue().set(redisKey, "ok", java.time.Duration.ofHours(24));
            return;
        }

        // Aplica o mapper
        String mpStatus = mpPayment.get("status").asText();
        PaymentStatus localStatus = MercadoPagoStatusMapper.mapMercadoPagoStatus(mpStatus);
        String statusDetail = mpPayment.has("status_detail") ? mpPayment.get("status_detail").asText() : null;

        // Não regride status
        if (statusOrderOf(localStatus) < statusOrderOf(payment.status())) {
            log.warn("Webhook tenta regredir status: {} -> {}", payment.status(), localStatus);
            redisTemplate.opsForValue().set(redisKey, "ok", java.time.Duration.ofHours(24));
            return;
        }

        // Atualiza se mudou
        if (payment.status() != localStatus) {
            Payment updated = payment.toBuilder()
                    .status(localStatus)
                    .statusDetail(statusDetail)
                    .externalId(externalPaymentId)
                    .updatedAt(Instant.now())
                    .build();
            if (mpPayment.has("date_approved")) {
                updated = updated.toBuilder()
                        .approvedAt(Instant.parse(mpPayment.get("date_approved").asText()))
                        .build();
            }
            Payment result = paymentRepository.save(updated);

            // Publica evento
            if (localStatus == PaymentStatus.captured) {
                PaymentApprovedEvent event = new PaymentApprovedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        payment.idOrder(),
                        payment.id(),
                        externalPaymentId,
                        payment.value(),
                        payment.method(),
                        result.approvedAt() != null ? result.approvedAt() : Instant.now());
                eventPublisher.publishPaymentApproved(event);
            } else if (localStatus == PaymentStatus.failed) {
                PaymentFailedEvent event = new PaymentFailedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        payment.idOrder(),
                        payment.id(),
                        "failed",
                        statusDetail);
                eventPublisher.publishPaymentFailed(event);
            }
        }

        redisTemplate.opsForValue().set(redisKey, "ok", java.time.Duration.ofHours(24));
    }

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
