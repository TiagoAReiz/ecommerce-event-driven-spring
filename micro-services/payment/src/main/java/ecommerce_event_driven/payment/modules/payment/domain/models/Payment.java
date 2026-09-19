package ecommerce_event_driven.payment.modules.payment.domain.models;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Payment(
        Long id,
        Long idOrder,
        BigDecimal value,
        PaymentStatus status,
        /** stripe, mercadopago... */
        String provider,
        /** Id da transacao no provider. */
        String externalId,
        /** Sem isso, retentativa vira cobranca dupla. */
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
