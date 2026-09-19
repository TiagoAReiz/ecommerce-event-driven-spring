package ecommerce_event_driven.user.modules.address.domain.models;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Address(
        Long id,
        Long idUser,
        /** Apelido do endereco: "casa", "trabalho". */
        String name,
        String zipcode,
        String country,
        String state,
        String city,
        String street,
        String number,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
