package ecommerce_event_driven.payment.modules.payment.infra.outbound.repos;

import ecommerce_event_driven.payment.modules.payment.infra.outbound.repos.entity.PaymentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    /**
     * De proposito sem filtro de deleted_at: payment_idempotency_uk e um indice
     * total. A retentativa precisa reencontrar a tentativa anterior mesmo que
     * ela tenha sido marcada como removida, senao vira cobranca dupla.
     */
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentEntity> findByIdOrder(Long idOrder);

    /** Busca por ID externo do Mercado Pago (usado em webhooks) */
    Optional<PaymentEntity> findByExternalId(String externalId);
}
