package ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos;

import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import java.util.List;
import java.util.Optional;

/**
 * Diferente dos outros modulos, aqui a leitura NAO esconde registros com
 * deleted_at preenchido: pagamento e registro financeiro, e uma cobranca
 * marcada como removida ainda precisa ser encontravel. Os indices da tabela
 * tambem sao totais, nao parciais.
 */
public interface PaymentRepositoryPort {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    /** Guarda de idempotencia: sem isso, retentativa vira cobranca dupla. */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByIdOrder(Long idOrder);

    /** Busca por ID externo do Mercado Pago (usado em webhooks) */
    Optional<Payment> findByExternalId(String externalId);
}
