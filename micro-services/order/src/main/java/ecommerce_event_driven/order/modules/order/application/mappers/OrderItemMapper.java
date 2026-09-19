package ecommerce_event_driven.order.modules.order.application.mappers;

import ecommerce_event_driven.order.modules.order.domain.models.OrderItem;
import ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity.OrderEntity;
import ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity.OrderItemEntity;

public final class OrderItemMapper {

    private OrderItemMapper() {
    }

    public static OrderItem toDomain(OrderItemEntity entity) {
        if (entity == null) {
            return null;
        }
        return OrderItem.builder()
                .id(entity.getId())
                .idProduct(entity.getIdProduct())
                .productName(entity.getProductName())
                .productPhotoUrl(entity.getProductPhotoUrl())
                .priceAtTime(entity.getPriceAtTime())
                .quantity(entity.getQuantity())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * O dono vem de fora porque o item nao carrega mais o id do pedido: quem
     * grava e a raiz do agregado, que ja tem a entidade em maos.
     */
    public static OrderItemEntity toEntity(OrderItem model, OrderEntity order) {
        if (model == null) {
            return null;
        }
        return OrderItemEntity.builder()
                .id(model.id())
                .order(order)
                .idProduct(model.idProduct())
                .productName(model.productName())
                .productPhotoUrl(model.productPhotoUrl())
                .priceAtTime(model.priceAtTime())
                .quantity(model.quantity())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .build();
    }
}
