package ecommerce_event_driven.order.modules.cart.application.mappers;

import ecommerce_event_driven.order.modules.cart.domain.models.CartItem;
import ecommerce_event_driven.order.modules.cart.infra.outbound.repos.entity.CartEntity;
import ecommerce_event_driven.order.modules.cart.infra.outbound.repos.entity.CartItemEntity;

public final class CartItemMapper {

    private CartItemMapper() {
    }

    public static CartItem toDomain(CartItemEntity entity) {
        if (entity == null) {
            return null;
        }
        return CartItem.builder()
                .id(entity.getId())
                .idCart(entity.getCart() == null ? null : entity.getCart().getId())
                .idProduct(entity.getIdProduct())
                .quantity(entity.getQuantity())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static CartItemEntity toEntity(CartItem model) {
        if (model == null) {
            return null;
        }
        CartEntity cart = null;
        if (model.idCart() != null) {
            cart = CartEntity.builder().id(model.idCart()).build();
        }
        return CartItemEntity.builder()
                .id(model.id())
                .cart(cart)
                .idProduct(model.idProduct())
                .quantity(model.quantity())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
