package ecommerce_event_driven.order.modules.cart.application.mappers;

import ecommerce_event_driven.order.modules.cart.domain.models.Cart;
import ecommerce_event_driven.order.modules.cart.infra.outbound.repos.entity.CartEntity;

public final class CartMapper {

    private CartMapper() {
    }

    public static Cart toDomain(CartEntity entity) {
        if (entity == null) {
            return null;
        }
        return Cart.builder()
                .id(entity.getId())
                .idUser(entity.getIdUser())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static CartEntity toEntity(Cart model) {
        if (model == null) {
            return null;
        }
        return CartEntity.builder()
                .id(model.id())
                .idUser(model.idUser())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
