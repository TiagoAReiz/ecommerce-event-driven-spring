package ecommerce_event_driven.inventory.modules.product.application.mappers;

import ecommerce_event_driven.inventory.modules.product.domain.models.Product;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.CategoryEntity;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductEntity;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static Product toDomain(ProductEntity entity) {
        if (entity == null) {
            return null;
        }
        return Product.builder()
                .id(entity.getId())
                // Ler so o id do proxy LAZY nao dispara carga da categoria.
                .idCategory(entity.getCategory() == null ? null : entity.getCategory().getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .stock(entity.getStock())
                .rating(entity.getRating())
                .ratingCount(entity.getRatingCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    /**
     * A categoria vira uma referencia so com id. Quem persiste deve usar
     * EntityManager.getReference(...) ou o proprio managed entity quando
     * precisar de uma associacao ligada a sessao.
     */
    public static ProductEntity toEntity(Product model) {
        if (model == null) {
            return null;
        }
        CategoryEntity category = null;
        if (model.idCategory() != null) {
            category = CategoryEntity.builder().id(model.idCategory()).build();
        }
        return ProductEntity.builder()
                .id(model.id())
                .category(category)
                .name(model.name())
                .description(model.description())
                .price(model.price())
                .stock(model.stock())
                .rating(model.rating())
                .ratingCount(model.ratingCount())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
