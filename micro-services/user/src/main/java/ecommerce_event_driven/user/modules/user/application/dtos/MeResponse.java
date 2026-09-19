package ecommerce_event_driven.user.modules.user.application.dtos;

import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.time.Instant;
import java.util.List;

/**
 * Perfil completo do usuario autenticado. Inclui todos os campos pessoais,
 * roles e timestamp.
 */
public record MeResponse(
        Long id,
        String name,
        String email,
        String cpf,
        String phone,
        String photoUrl,
        List<String> roles,
        long addressCount,
        Instant createdAt,
        Instant updatedAt) {

    public static MeResponse from(User user, boolean storeOwner, long addressCount) {
        List<String> roles = storeOwner ? List.of("customer", "owner") : List.of("customer");
        return new MeResponse(
                user.id(),
                user.name(),
                user.email(),
                user.cpf(),
                user.phone(),
                user.photoUrl(),
                roles,
                addressCount,
                user.createdAt(),
                user.updatedAt());
    }
}
