package ecommerce_event_driven.user.modules.user.domain.models;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record User(
        Long id,
        String name,
        String email,
        String googleSub,
        String cpf,
        String phone,
        String photoUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
