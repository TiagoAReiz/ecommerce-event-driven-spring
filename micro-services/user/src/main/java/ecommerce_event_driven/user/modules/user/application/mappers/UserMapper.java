package ecommerce_event_driven.user.modules.user.application.mappers;

import ecommerce_event_driven.user.modules.user.domain.models.User;
import ecommerce_event_driven.user.modules.user.infra.outbound.repos.entity.UserEntity;

public final class UserMapper {

    private UserMapper() {
    }

    public static User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        return User.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .googleSub(entity.getGoogleSub())
                .cpf(entity.getCpf())
                .phone(entity.getPhone())
                .photoUrl(entity.getPhotoUrl())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static UserEntity toEntity(User model) {
        if (model == null) {
            return null;
        }
        return UserEntity.builder()
                .id(model.id())
                .name(model.name())
                .email(model.email())
                .googleSub(model.googleSub())
                .cpf(model.cpf())
                .phone(model.phone())
                .photoUrl(model.photoUrl())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
