package ecommerce_event_driven.user.modules.user.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.time.LocalDate;

/**
 * Perfil publico reduzido de um usuario: so nome, foto e data de inclusao.
 * Nunca expoe email, cpf, phone ou googleSub.
 */
public record PublicUserResponse(
        Long id,
        String name,
        String photoUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate memberSince) {

    public static PublicUserResponse from(User user) {
        LocalDate memberSince = user.createdAt() != null
                ? LocalDate.from(user.createdAt())
                : LocalDate.now();
        return new PublicUserResponse(user.id(), user.name(), user.photoUrl(), memberSince);
    }
}
