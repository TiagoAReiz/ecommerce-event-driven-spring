package ecommerce_event_driven.payment.modules.payment.application.usecases;

import ecommerce_event_driven.payment.config.InvalidEventException;
import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentApprovedEvent;
import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentFailedEvent;
import ecommerce_event_driven.payment.modules.payment.application.mappers.MercadoPagoStatusMapper;
import ecommerce_event_driven.payment.modules.payment.application.mappers.PaymentMapper;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos.PaymentRepositoryPort;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.modules.payment.domain.models.PaymentStatus;
import ecommerce_event_driven.payment.modules.payment.infra.outbound.events.PaymentEventOutboxPublisher;
import ecommerce_event_driven.payment.shared.client.OrderServiceClient;
import ecommerce_event_driven.payment.shared.web.ConflictException;
import ecommerce_event_driven.payment.shared.web.ForbiddenException;
import ecommerce_event_driven.payment.shared.web.NotFoundException;
import ecommerce_event_driven.payment.shared.web.UnprocessableException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class CreatePaymentUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreatePaymentUseCase.class);
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final OrderServiceClient orderServiceClient;
    private final PaymentEventOutboxPublisher eventPublisher;

    public CreatePaymentUseCase(
            PaymentRepositoryPort paymentRepository,
            PaymentGatewayPort paymentGateway,
            OrderServiceClient orderServiceClient,
            PaymentEventOutboxPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.orderServiceClient = orderServiceClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Payment execute(
            Long orderId, String method, String idempotencyKey, Long currentUserId, JsonNode requestBody) {
        // Valida idempotencia
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Payment p = existing.get();
            if (!p.idOrder().equals(orderId) || !p.method().equals(method)) {
                throw new UnprocessableException("Idempotency key reused", "IDEMPOTENCY_KEY_REUSED");
            }
            return p;
        }

        // Busca o pedido
        JsonNode order = orderServiceClient.getOrder(orderId);
        if (order == null) {
            throw new NotFoundException("Pedido não encontrado");
        }

        Long idCustomer = order.get("idCustomer").asLong();
        if (!idCustomer.equals(currentUserId)) {
            throw new ForbiddenException("Pedido pertence a outro cliente");
        }

        String orderStatus = order.get("status").asText();
        if (!"pending".equals(orderStatus)) {
            throw new ConflictException("Pedido não está em pending", "ORDER_NOT_PENDING");
        }

        // Verifica se já existe pagamento capturado ou autorizado
        var payments = paymentRepository.findByIdOrder(orderId);
        for (Payment p : payments) {
            if (p.status() == PaymentStatus.captured || p.status() == PaymentStatus.authorized) {
                throw new ConflictException("Pedido já tem pagamento capturado", "PAYMENT_ALREADY_CAPTURED");
            }
        }

        BigDecimal totalCost = new BigDecimal(order.get("totalCost").asText());

        // Grava pending
        Payment pending = Payment.builder()
                .idOrder(orderId)
                .value(totalCost)
                .status(PaymentStatus.pending)
                .provider("mercadopago")
                .idempotencyKey(idempotencyKey)
                .method(method)
                .refundedAmount(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        Payment saved = paymentRepository.save(pending);
        Long paymentId = saved.id();

        // Chama o gateway FORA de transacao
        JsonNode gatewayResponse;
        try {
            if ("pix".equals(method)) {
                String email = requestBody.get("payer").get("email").asText();
                String cpf = requestBody.get("payer").get("identification").get("number").asText();
                gatewayResponse = paymentGateway.createPix(paymentId, totalCost, email, cpf, idempotencyKey);
            } else if ("credit_card".equals(method)) {
                String token = requestBody.get("token").asText();
                String paymentMethodId = requestBody.get("paymentMethodId").asText();
                String issuerId = requestBody.get("issuerId").asText();
                int installments = requestBody.get("installments").asInt();
                String email = requestBody.get("payer").get("email").asText();
                String cpf = requestBody.get("payer").get("identification").get("number").asText();
                gatewayResponse = paymentGateway.createCard(
                        paymentId, totalCost, token, paymentMethodId, issuerId, installments, email, cpf, idempotencyKey);
            } else if ("checkout_pro".equals(method)) {
                gatewayResponse = paymentGateway.createPreference(
                        paymentId, totalCost, "Pedido #" + orderId, idempotencyKey);
            } else {
                throw new IllegalArgumentException("Method desconhecido");
            }
        } catch (Exception e) {
            log.error("Erro ao chamar gateway", e);
            throw e;
        }

        // Grava resultado (transacao 2)
        return updatePaymentFromGateway(saved, gatewayResponse);
    }

    @Transactional
    private Payment updatePaymentFromGateway(Payment payment, JsonNode gatewayResponse) {
        String externalId = gatewayResponse.get("id").asText();
        String mpStatus = gatewayResponse.get("status").asText();
        PaymentStatus localStatus = MercadoPagoStatusMapper.mapMercadoPagoStatus(mpStatus);
        String statusDetail = gatewayResponse.has("status_detail")
                ? gatewayResponse.get("status_detail").asText()
                : null;

        Payment updated = payment.toBuilder()
                .externalId(externalId)
                .status(localStatus)
                .statusDetail(statusDetail)
                .updatedAt(Instant.now())
                .build();

        // Grava detail conforme o method
        if ("pix".equals(payment.method()) && gatewayResponse.has("point_of_interaction")) {
            JsonNode txData = gatewayResponse.get("point_of_interaction").get("transaction_data");
            updated = updated.toBuilder()
                    .qrCode(txData.get("qr_code").asText())
                    .qrCodeBase64(txData.get("qr_code_base64").asText())
                    .ticketUrl(txData.get("ticket_url").asText())
                    .expiresAt(Instant.parse(gatewayResponse.get("date_of_expiration").asText()))
                    .build();
        } else if ("credit_card".equals(payment.method()) && gatewayResponse.has("card")) {
            JsonNode card = gatewayResponse.get("card");
            updated = updated.toBuilder()
                    .cardBrand(card.get("brand").asText())
                    .cardLast4(card.get("last_four_digits").asText())
                    .build();
        } else if ("checkout_pro".equals(payment.method())) {
            updated = updated.toBuilder()
                    .initPoint(gatewayResponse.get("init_point").asText())
                    .expiresAt(Instant.parse(gatewayResponse.get("expiration_date_to").asText()))
                    .build();
        }

        Payment result = paymentRepository.save(updated);

        // Publica evento se status mudou
        if (localStatus == PaymentStatus.captured) {
            PaymentApprovedEvent event = new PaymentApprovedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    payment.idOrder(),
                    result.id(),
                    externalId,
                    payment.value(),
                    payment.method(),
                    Instant.now());
            eventPublisher.publishPaymentApproved(event);
        } else if (localStatus == PaymentStatus.failed) {
            PaymentFailedEvent event = new PaymentFailedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    payment.idOrder(),
                    result.id(),
                    "failed",
                    statusDetail);
            eventPublisher.publishPaymentFailed(event);
        }

        return result;
    }
}
