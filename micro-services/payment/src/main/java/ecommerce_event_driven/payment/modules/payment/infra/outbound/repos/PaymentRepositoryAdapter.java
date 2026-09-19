package ecommerce_event_driven.payment.modules.payment.infra.outbound.repos;

import ecommerce_event_driven.payment.modules.payment.application.mappers.PaymentMapper;
import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos.PaymentRepositoryPort;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        return PaymentMapper.toDomain(jpaRepository.save(PaymentMapper.toEntity(payment)));
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpaRepository.findById(id).map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(PaymentMapper::toDomain);
    }

    @Override
    public List<Payment> findByIdOrder(Long idOrder) {
        return jpaRepository.findByIdOrder(idOrder).stream()
                .map(PaymentMapper::toDomain)
                .toList();
    }
}
