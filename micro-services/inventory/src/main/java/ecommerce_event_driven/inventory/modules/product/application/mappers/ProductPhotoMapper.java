package ecommerce_event_driven.inventory.modules.product.application.mappers;

import ecommerce_event_driven.inventory.modules.product.domain.models.ProductPhoto;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductEntity;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductPhotoEntity;

public final class ProductPhotoMapper {

    private ProductPhotoMapper() {
    }

    public static ProductPhoto toDomain(ProductPhotoEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProductPhoto.builder()
                .id(entity.getId())
                .idProduct(entity.getProduct() == null ? null : entity.getProduct().getId())
                .photoUrl(entity.getPhotoUrl())
                .position(entity.getPosition())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static ProductPhotoEntity toEntity(ProductPhoto model) {
        if (model == null) {
            return null;
        }
        ProductEntity product = null;
        if (model.idProduct() != null) {
            product = ProductEntity.builder().id(model.idProduct()).build();
        }
        return ProductPhotoEntity.builder()
                .id(model.id())
                .product(product)
                .photoUrl(model.photoUrl())
                .position(model.position())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
