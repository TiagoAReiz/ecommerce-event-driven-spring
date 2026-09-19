package ecommerce_event_driven.user.modules.user.application.dtos;

import ecommerce_event_driven.user.modules.user.domain.models.User;

/**
 * Snapshot reduzido de usuario para hidratacao em lote (avaliacoes, etc).
 */
public record InternalUserResponse(Long id, String name, String photoUrl) {
    public static InternalUserResponse from(User user) {
        return new InternalUserResponse(user.id(), user.name(), user.photoUrl());
    }
}
