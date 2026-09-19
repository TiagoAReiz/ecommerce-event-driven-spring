package ecommerce_event_driven.payment.modules.payment.infra.inbound.controllers;

import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentResponse;
import ecommerce_event_driven.payment.modules.payment.application.usecases.CancelPaymentUseCase;
import ecommerce_event_driven.payment.modules.payment.application.usecases.GetPaymentMethodsUseCase;
import ecommerce_event_driven.payment.modules.payment.application.usecases.ListPaymentsUseCase;
import ecommerce_event_driven.payment.modules.payment.application.usecases.CreatePaymentUseCase;
import ecommerce_event_driven.payment.modules.payment.application.usecases.GetConfigUseCase;
import ecommerce_event_driven.payment.modules.payment.application.usecases.GetPaymentUseCase;
import ecommerce_event_driven.payment.modules.payment.application.usecases.RefundPaymentUseCase;
import ecommerce_event_driven.payment.modules.payment.application.usecases.SyncPaymentUseCase;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.shared.security.CurrentUser;
import ecommerce_event_driven.payment.shared.web.BadRequestException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private final CreatePaymentUseCase createPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final GetConfigUseCase getConfigUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;
    private final SyncPaymentUseCase syncPaymentUseCase;
    private final CancelPaymentUseCase cancelPaymentUseCase;
    private final ListPaymentsUseCase listPaymentsUseCase;
    private final GetPaymentMethodsUseCase getPaymentMethodsUseCase;
    private final CurrentUser currentUser;

    public PaymentController(
            CreatePaymentUseCase createPaymentUseCase,
            GetPaymentUseCase getPaymentUseCase,
            GetConfigUseCase getConfigUseCase,
            RefundPaymentUseCase refundPaymentUseCase,
            SyncPaymentUseCase syncPaymentUseCase,
            CancelPaymentUseCase cancelPaymentUseCase,
            ListPaymentsUseCase listPaymentsUseCase,
            GetPaymentMethodsUseCase getPaymentMethodsUseCase,
            CurrentUser currentUser) {
        this.createPaymentUseCase = createPaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
        this.getConfigUseCase = getConfigUseCase;
        this.refundPaymentUseCase = refundPaymentUseCase;
        this.syncPaymentUseCase = syncPaymentUseCase;
        this.cancelPaymentUseCase = cancelPaymentUseCase;
        this.listPaymentsUseCase = listPaymentsUseCase;
        this.getPaymentMethodsUseCase = getPaymentMethodsUseCase;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<?> createPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key é obrigatório", "MISSING_IDEMPOTENCY_KEY");
        }
        try {
            UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Idempotency-Key não é uma UUID válida", "INVALID_IDEMPOTENCY_KEY");
        }

        Long orderId = request.get("idOrder").asLong();
        String method = request.get("method").asText();
        Long currentUserId = currentUser.getId();

        Payment payment = createPaymentUseCase.execute(orderId, method, idempotencyKey, currentUserId, request);

        return ResponseEntity.status(201).body(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPayment(@PathVariable Long id) {
        // O papel vem na claim roles; nao existe autoridade SCOPE_owner.
        Payment payment = getPaymentUseCase.execute(id, currentUser.getId(), currentUser.isOwner());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @GetMapping
    public ResponseEntity<?> listPayments(@RequestParam Long orderId) {
        var payments = listPaymentsUseCase.forCustomer(orderId, currentUser.getId(), currentUser.isOwner());
        return ResponseEntity.ok(payments.stream().map(PaymentResponse::from).toList());
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(getConfigUseCase.execute());
    }

    @GetMapping("/methods")
    public ResponseEntity<?> getPaymentMethods() {
        return ResponseEntity.ok(getPaymentMethodsUseCase.execute());
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<?> syncPayment(@PathVariable Long id) {
        SyncPaymentUseCase.SyncResponse result = syncPaymentUseCase.execute(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelPayment(@PathVariable Long id) {
        Payment payment = cancelPaymentUseCase.execute(id);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refundPayment(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) JsonNode request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key é obrigatório", "MISSING_IDEMPOTENCY_KEY");
        }

        BigDecimal amount = request != null && request.has("amount") ? new BigDecimal(request.get("amount").asText())
                : null;
        Payment payment = refundPaymentUseCase.refundManual(id, amount, idempotencyKey);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }
}
