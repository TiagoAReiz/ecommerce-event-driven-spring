package ecommerce_event_driven.inventory.modules.product.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.StockReservationRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.models.ReservationStatus;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductEntity;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.StockReservationEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StockReservationRepositoryAdapter implements StockReservationRepositoryPort {

    private final StockReservationJpaRepository jpaRepository;
    private final ProductJpaRepository productJpaRepository;

    public StockReservationRepositoryAdapter(StockReservationJpaRepository jpaRepository,
            ProductJpaRepository productJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.productJpaRepository = productJpaRepository;
    }

    /**
     * Travar, somar, inserir — nessa ordem, dentro da transacao de quem chamou.
     *
     * <p>O lock da linha do produto vem primeiro justamente para que a soma
     * seguinte enxergue um estado estavel: enquanto esta transacao nao commita,
     * nenhuma outra reserva do mesmo produto passa daqui.
     */
    @Override
    public boolean hold(Long idOrder, Long idProduct, int quantity, Instant expiresAt) {
        Optional<ProductEntity> locked = productJpaRepository.findByIdForUpdate(idProduct);
        if (locked.isEmpty()) {
            return false;
        }

        int reserved = jpaRepository.sumActiveQuantity(idProduct, ReservationStatus.held, Instant.now());
        if (locked.get().getStock() - reserved < quantity) {
            return false;
        }

        jpaRepository.save(StockReservationEntity.builder()
                .idOrder(idOrder)
                .idProduct(idProduct)
                .quantity(quantity)
                .status(ReservationStatus.held)
                .expiresAt(expiresAt)
                .build());
        return true;
    }

    @Override
    public boolean existsByIdOrder(Long idOrder) {
        return jpaRepository.existsByIdOrder(idOrder);
    }

    @Override
    public int releaseHeldByOrder(Long idOrder) {
        return jpaRepository.updateStatusByOrder(
                idOrder, ReservationStatus.held, ReservationStatus.released);
    }
}
