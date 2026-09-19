package ecommerce_event_driven.inventory.modules.product.application.mappers;

import ecommerce_event_driven.inventory.modules.product.domain.models.Category;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.CategoryEntity;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static Category toDomain(CategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return Category.builder()
                .id(entity.getId())
                .name(entity.getName())
                .slug(entity.getSlug())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static CategoryEntity toEntity(Category model) {
        if (model == null) {
            return null;
        }
        return CategoryEntity.builder()
                .id(model.id())
                .name(model.name())
                .slug(model.slug())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
