package ecommerce_event_driven.user.modules.user.application.dtos;

import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.util.List;

/**
 * Perfil completo com papeis para rotas internas (autorizar, auditar).
 */
public record InternalUserProfileResponse(
        Long id,
        String name,
        String email,
        String photoUrl,
        List<String> roles) {

    public static InternalUserProfileResponse from(User user, List<String> roles) {
        return new InternalUserProfileResponse(user.id(), user.name(), user.email(), user.photoUrl(), roles);
    }
}
