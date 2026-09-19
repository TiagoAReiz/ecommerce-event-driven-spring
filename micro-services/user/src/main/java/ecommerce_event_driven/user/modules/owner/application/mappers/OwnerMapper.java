package ecommerce_event_driven.user.modules.owner.application.mappers;

import ecommerce_event_driven.user.modules.owner.domain.models.Owner;
import ecommerce_event_driven.user.modules.owner.infra.outbound.repos.entity.OwnerEntity;

public final class OwnerMapper {

    private OwnerMapper() {
    }

    public static Owner toDomain(OwnerEntity entity) {
        if (entity == null) {
            return null;
        }
        return Owner.builder()
                .id(entity.getId())
                .idUser(entity.getIdUser())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static OwnerEntity toEntity(Owner model) {
        if (model == null) {
            return null;
        }
        return OwnerEntity.builder()
                .id(model.id())
                .idUser(model.idUser())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
