package ecommerce_event_driven.order.modules.order.infra.inbound.controllers;

import ecommerce_event_driven.order.modules.order.application.dtos.OrderDetailResponse;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderDetailResponse.ItemDetail;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderDetailResponse.PaymentProjection;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderDetailResponse.ShipmentProjection;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import ecommerce_event_driven.order.shared.web.BadRequestException;
import ecommerce_event_driven.order.shared.web.ConflictException;
import ecommerce_event_driven.order.shared.web.NotFoundException;
import ecommerce_event_driven.order.shared.web.UnprocessableException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador interno para operacoes de hidratacao de pedidos.
 * Protegido com escopo internal:hydrate.
 */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {
    private final OrderRepositoryPort orderRepo;

    public InternalOrderController(OrderRepositoryPort orderRepo) {
        this.orderRepo = orderRepo;
    }

    /**
     * GET /internal/orders/{id} - Obtem um pedido com dados para hidratacao.
     * Usado por outros servicos internos.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InternalOrderResponse> getOrder(@PathVariable Long id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Pedido nao encontrado"));

        return ResponseEntity.ok(toInternalResponse(order));
    }

    /**
     * PATCH /internal/orders/{id}/status - Altera o status do pedido manualmente.
     * Para reconciliacao apenas.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDetailResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {

        if (request.reason() == null || request.reason().trim().isEmpty()) {
            throw new UnprocessableException("MISSING_REASON", "Campo 'reason' e obrigatorio");
        }

        if (request.reason().length() > 500) {
            throw new BadRequestException("REASON_TOO_LONG", "Razao nao pode exceder 500 caracteres");
        }

        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Pedido nao encontrado"));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("INVALID_STATUS", "Status invalido: " + request.status());
        }

        // Validar transicao
        if (!canTransition(order.status(), newStatus)) {
            throw new ConflictException("INVALID_STATE_TRANSITION",
                "Transicao de " + order.status() + " para " + newStatus + " nao e permitida");
        }

        // Atualizar status
        boolean updated = orderRepo.updateStatus(id, order.status(), newStatus);
        if (!updated) {
            throw new ConflictException("UPDATE_FAILED", "Falha ao atualizar status");
        }

        // Recarregar e retornar
        Order updatedOrder = orderRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Pedido nao encontrado"));

        return ResponseEntity.ok(toDetailResponse(updatedOrder));
    }

    private boolean canTransition(OrderStatus from, OrderStatus to) {
        // Permitir apenas transicoes bem definidas
        return switch (from) {
            case pending -> to == OrderStatus.paid || to == OrderStatus.cancelled;
            case paid -> to == OrderStatus.processing || to == OrderStatus.cancelled;
            case processing -> to == OrderStatus.shipped || to == OrderStatus.cancelled;
            case shipped -> to == OrderStatus.delivered;
            default -> false;
        };
    }

    private InternalOrderResponse toInternalResponse(Order order) {
        List<InternalOrderResponse.InternalItem> items = order.items().stream()
                .map(item -> new InternalOrderResponse.InternalItem(
                        item.idProduct(),
                        item.productName(),
                        item.priceAtTime(),
                        item.quantity()
                ))
                .toList();

        return new InternalOrderResponse(
                order.id(),
                order.idCustomer(),
                order.status().name(),
                order.idAddress(),
                order.itemsCost(),
                order.freightCost(),
                order.totalCost(),
                items
        );
    }

    private OrderDetailResponse toDetailResponse(Order order) {
        List<ItemDetail> items = order.items().stream()
                .map(item -> new ItemDetail(
                        item.id(),
                        item.idProduct(),
                        item.productName(),
                        item.productPhotoUrl(),
                        item.priceAtTime(),
                        item.quantity(),
                        item.priceAtTime().multiply(java.math.BigDecimal.valueOf(item.quantity()))
                ))
                .toList();

        PaymentProjection payment = order.paymentId() != null ?
                new PaymentProjection(order.paymentId(), order.paymentStatus(), "mercadopago") : null;

        ShipmentProjection shipment = order.shipmentId() != null ?
                new ShipmentProjection(order.shipmentId(), order.shipmentStatus(), order.trackingCode()) : null;

        return new OrderDetailResponse(
                order.id(),
                order.status().name(),
                order.idAddress(),
                items,
                order.itemsCost(),
                order.freightCost(),
                order.totalCost(),
                payment,
                shipment,
                order.createdAt(),
                order.updatedAt()
        );
    }

    /**
     * Response para hidratacao interna.
     */
    public record InternalOrderResponse(
            Long id,
            Long idCustomer,
            String status,
            Long idAddress,
            java.math.BigDecimal itemsCost,
            java.math.BigDecimal freightCost,
            java.math.BigDecimal totalCost,
            List<InternalItem> items
    ) {
        public record InternalItem(
                Long idProduct,
                String productName,
                java.math.BigDecimal priceAtTime,
                Integer quantity
        ) {}
    }

    /**
     * Request para alteracao de status.
     */
    public record StatusUpdateRequest(
            String status,
            String reason
    ) {}
}
