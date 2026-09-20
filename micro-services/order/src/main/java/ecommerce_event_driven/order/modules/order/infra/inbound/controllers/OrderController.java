package ecommerce_event_driven.order.modules.order.infra.inbound.controllers;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import ecommerce_event_driven.order.modules.order.application.dtos.*;
import ecommerce_event_driven.order.modules.order.application.usecases.*;
import ecommerce_event_driven.order.shared.security.CurrentUser;
import ecommerce_event_driven.order.shared.web.BadRequestException;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controlador para operacoes de pedido.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final CheckoutService checkoutService;
    private final ecommerce_event_driven.order.shared.idempotency.IdempotencyStore idempotencyStore;
    private final tools.jackson.databind.json.JsonMapper jsonMapper;
    private final CurrentUser currentUser;
    private final ListMyOrdersUseCase listMyOrdersUseCase;
    private final GetMyOrderUseCase getMyOrderUseCase;
    private final ListStoreOrdersUseCase listStoreOrdersUseCase;
    private final CancelOrderService cancelOrderService;

    public OrderController(CheckoutService checkoutService,
            ecommerce_event_driven.order.shared.idempotency.IdempotencyStore idempotencyStore,
            tools.jackson.databind.json.JsonMapper jsonMapper, CurrentUser currentUser,
                          ListMyOrdersUseCase listMyOrdersUseCase, GetMyOrderUseCase getMyOrderUseCase,
                          ListStoreOrdersUseCase listStoreOrdersUseCase, CancelOrderService cancelOrderService) {
        this.checkoutService = checkoutService;
        this.idempotencyStore = idempotencyStore;
        this.jsonMapper = jsonMapper;
        this.currentUser = currentUser;
        this.listMyOrdersUseCase = listMyOrdersUseCase;
        this.getMyOrderUseCase = getMyOrderUseCase;
        this.listStoreOrdersUseCase = listStoreOrdersUseCase;
        this.cancelOrderService = cancelOrderService;
    }

    /**
     * POST /orders - Cria um novo pedido com checkout.
     */
    @PostMapping
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            JwtAuthenticationToken token) {
        // Validar header obrigatorio
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BadRequestException("MISSING_IDEMPOTENCY_KEY", "Header Idempotency-Key e obrigatorio");
        }

        // Validar que e UUID (simples check)
        try {
            java.util.UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key deve ser UUID");
        }

        Long userId = currentUser.getId(token.getToken());
        String bodyHash = idempotencyStore.hash(jsonMapper.writeValueAsString(request));

        // Repeticao da mesma chave: devolve a resposta gravada, ou recusa se o corpo mudou.
        var previous = idempotencyStore.getResult(userId, idempotencyKey);
        if (previous != null) {
            if (!previous.bodyHash().equals(bodyHash)) {
                throw new ecommerce_event_driven.order.shared.web.UnprocessableException(
                        "IDEMPOTENCY_KEY_REUSED", "Idempotency-Key ja usada com outro corpo");
            }
            return ResponseEntity.status(previous.status())
                    .header("Idempotency-Replayed", "true")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(previous.body());
        }
        if (!idempotencyStore.markInFlight(userId, idempotencyKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("Retry-After", "1")
                    .body(java.util.Map.of("code", "IDEMPOTENCY_IN_FLIGHT",
                            "detail", "Requisicao com esta chave ainda em processamento"));
        }

        try {
            // O parametro de token dos clients nao e usado: chamadas internas levam o token de servico.
            CreateOrderResponse order = checkoutService.checkout(userId, request, null);
            idempotencyStore.storeResult(userId, idempotencyKey, bodyHash,
                    HttpStatus.CREATED.value(), jsonMapper.writeValueAsString(order));
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (RuntimeException e) {
            idempotencyStore.release(userId, idempotencyKey);
            throw e;
        }
    }

    /**
     * GET /orders - Lista pedidos do usuario com filtros.
     */
    @GetMapping
    public ResponseEntity<OrderListResponse> listOrders(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            JwtAuthenticationToken token) {
        Long userId = currentUser.getId(token.getToken());

        // Parse status filters
        List<OrderStatus> statuses = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            for (String s : status) {
                try {
                    statuses.add(OrderStatus.valueOf(s));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("INVALID_STATUS", "Status invalido: " + s);
                }
            }
        }

        // Parse dates
        LocalDate fromDate = null, toDate = null;
        if (from != null && !from.isEmpty()) {
            try {
                fromDate = LocalDate.parse(from, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                throw new BadRequestException("INVALID_DATE", "Formato de 'from' invalido");
            }
        }
        if (to != null && !to.isEmpty()) {
            try {
                toDate = LocalDate.parse(to, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                throw new BadRequestException("INVALID_DATE", "Formato de 'to' invalido");
            }
        }

        OrderListResponse result = listMyOrdersUseCase.execute(userId, statuses.isEmpty() ? null : statuses,
                fromDate, toDate, page, size, sort);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /orders/{id} - Obtem detalhes do pedido do usuario.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> getOrder(
            @PathVariable Long id,
            JwtAuthenticationToken token) {
        Long userId = currentUser.getId(token.getToken());
        OrderDetailResponse result = getMyOrderUseCase.execute(id, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /orders/manage - Lista todos os pedidos (para gerencia da loja).
     * NOTA: Colocado ANTES de /{id} para evitar rota conflitante.
     */
    @GetMapping("/manage")
    public ResponseEntity<OrderListResponse> listAllOrders(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            JwtAuthenticationToken token) {
        // Parse status filters
        List<OrderStatus> statuses = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            for (String s : status) {
                try {
                    statuses.add(OrderStatus.valueOf(s));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("INVALID_STATUS", "Status invalido: " + s);
                }
            }
        }

        // Parse dates
        LocalDate fromDate = null, toDate = null;
        if (from != null && !from.isEmpty()) {
            try {
                fromDate = LocalDate.parse(from, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                throw new BadRequestException("INVALID_DATE", "Formato de 'from' invalido");
            }
        }
        if (to != null && !to.isEmpty()) {
            try {
                toDate = LocalDate.parse(to, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                throw new BadRequestException("INVALID_DATE", "Formato de 'to' invalido");
            }
        }

        OrderListResponse result = listStoreOrdersUseCase.execute(statuses.isEmpty() ? null : statuses,
                customerId, fromDate, toDate, page, size, sort);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /orders/{id}/cancel - Cancela um pedido.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderDetailResponse> cancelOrder(
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest request,
            JwtAuthenticationToken token) {
        Long userId = currentUser.getId(token.getToken());
        
        // Validar reason
        if (request.reason() == null || request.reason().trim().isEmpty()) {
            throw new BadRequestException("MISSING_REASON", "Campo 'reason' e obrigatorio");
        }

        if (request.reason().length() > 500) {
            throw new BadRequestException("REASON_TOO_LONG", "Razao nao pode exceder 500 caracteres");
        }

        // O papel vem do token: processing so a loja cancela (docs/api-contracts.md, §8 cancel).
        boolean isOwner = currentUser.getRoles(token.getToken()).contains("owner");
        cancelOrderService.execute(id, userId, request.reason(), isOwner);

        // Recarregar e retornar pedido atualizado
        OrderDetailResponse result = getMyOrderUseCase.execute(id, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Request para cancelamento.
     */
    public record CancelOrderRequest(
            String reason
    ) {}
}
