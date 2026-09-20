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
import ecommerce_event_driven.payment.shared.web.BadRequestException;
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

        // Valida o corpo antes de gravar: um 400 nao pode deixar pagamento pendente orfao
        // queimando a Idempotency-Key.
        if ("pix".equals(method)) {
            requiredText(requestBody, "payer.email");
            requiredText(requestBody, "payer.identification.number");
        } else if ("credit_card".equals(method)) {
            requiredText(requestBody, "token");
            requiredText(requestBody, "paymentMethodId");
            requiredText(requestBody, "issuerId");
            requiredInt(requestBody, "installments");
            requiredText(requestBody, "payer.email");
            requiredText(requestBody, "payer.identification.number");
        } else if (!"checkout_pro".equals(method)) {
            throw new BadRequestException("Method desconhecido: " + method, "VALIDATION_ERROR");
        }

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

        // Chamada ao provedor. Roda dentro da transacao de execute(): o registro pending e a
        // resposta do gateway precisam cair juntos com o evento da outbox.
        JsonNode gatewayResponse;
        try {
            if ("pix".equals(method)) {
                String email = requiredText(requestBody, "payer.email");
                String cpf = requiredText(requestBody, "payer.identification.number");
                gatewayResponse = paymentGateway.createPix(paymentId, totalCost, email, cpf, idempotencyKey);
            } else if ("credit_card".equals(method)) {
                String token = requiredText(requestBody, "token");
                String paymentMethodId = requiredText(requestBody, "paymentMethodId");
                String issuerId = requiredText(requestBody, "issuerId");
                int installments = requiredInt(requestBody, "installments");
                String email = requiredText(requestBody, "payer.email");
                String cpf = requiredText(requestBody, "payer.identification.number");
                gatewayResponse = paymentGateway.createCard(
                        paymentId, totalCost, token, paymentMethodId, issuerId, installments, email, cpf, idempotencyKey);
            } else if ("checkout_pro".equals(method)) {
                gatewayResponse = paymentGateway.createPreference(
                        paymentId, totalCost, "Pedido #" + orderId, idempotencyKey);
            } else {
                throw new BadRequestException("Method desconhecido: " + method, "VALIDATION_ERROR");
            }
        } catch (Exception e) {
            log.error("Erro ao chamar gateway", e);
            throw e;
        }

        // Grava resultado (transacao 2)
        return updatePaymentFromGateway(saved, gatewayResponse);
    }

    /**
     * Le um campo obrigatorio do corpo (caminho com ponto) como texto.
     *
     * <p>Corpo fora do contrato e erro do cliente: sem isso um campo ausente virava
     * NullPointerException e o cliente recebia 500 no lugar de 400.
     */
    private static String requiredText(JsonNode body, String path) {
        JsonNode node = at(body, path);
        String value = node == null || node.isNull() ? null : node.asText();
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Campo obrigatorio ausente: " + path, "VALIDATION_ERROR");
        }
        return value;
    }

    private static int requiredInt(JsonNode body, String path) {
        JsonNode node = at(body, path);
        if (node == null || !node.isNumber()) {
            throw new BadRequestException("Campo obrigatorio ausente: " + path, "VALIDATION_ERROR");
        }
        return node.asInt();
    }

    private static JsonNode at(JsonNode body, String path) {
        JsonNode node = body;
        for (String field : path.split("[.]")) {
            if (node == null || !node.isObject()) {
                return null;
            }
            node = node.get(field);
        }
        return node;
    }

    /** Campo de data opcional no provedor: ausente nao pode derrubar a cobranca. */
    private static Instant instantOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(node.get(field).asText()).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

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
                    .expiresAt(instantOrNull(gatewayResponse, "date_of_expiration"))
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
                    .expiresAt(instantOrNull(gatewayResponse, "expiration_date_to"))
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
