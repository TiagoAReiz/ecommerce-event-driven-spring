package ecommerce_event_driven.inventory.modules.review.domain.models;

import java.time.Instant;
import lombok.Builder;

/** Prova, vinda do evento OrderDelivered, de que a compra existiu. */
@Builder(toBuilder = true)
public record ReviewEligibility(
        Long idUser,
        Long idProduct,
        Long idOrder,
        Instant grantedAt) {
}
