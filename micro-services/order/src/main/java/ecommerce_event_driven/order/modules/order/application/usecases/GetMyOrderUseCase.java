package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.modules.order.application.dtos.OrderDetailResponse;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderDetailResponse.ItemDetail;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderDetailResponse.PaymentProjection;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderDetailResponse.ShipmentProjection;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.shared.web.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Obtem os detalhes de um pedido do usuario (com projecoes).
 */
@Service
public class GetMyOrderUseCase {
    private final OrderRepositoryPort orderRepo;

    public GetMyOrderUseCase(OrderRepositoryPort orderRepo) {
        this.orderRepo = orderRepo;
    }

    /**
     * Obtem um pedido especifico do usuario.
     * @param orderId ID do pedido
     * @param customerId ID do usuario (validacao de propriedade)
     * @return Detalhes do pedido com projecoes
     * @throws NotFoundException Se o pedido nao existe ou nao pertence ao usuario
     */
    public OrderDetailResponse execute(Long orderId, Long customerId) {
        Order order = orderRepo.findByIdAndIdCustomer(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Pedido nao encontrado"));

        return toDetail(order);
    }

    private OrderDetailResponse toDetail(Order order) {
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
}
