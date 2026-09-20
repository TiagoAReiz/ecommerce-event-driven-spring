package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.StockReservationRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.models.ReservationStatus;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.StockReservationJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.StockReservationEntity;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transacao separada para liberar confirmed e devolver estoque.
 *
 * <p>Operacao explicitamente diferente da liberacao de held, porque
 * held nunca mexe em estoque (ele nao foi debitado), mas confirmed
 * precisa devolver o que foi baixado.
 */
@Component
class ReleaseConfirmedStockTransaction {

    private final StockReservationJpaRepository jpaRepository;
    private final ProductJpaRepository productJpaRepository;

    ReleaseConfirmedStockTransaction(
            StockReservationJpaRepository jpaRepository,
            ProductJpaRepository productJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.productJpaRepository = productJpaRepository;
    }

    /**
     * Libera reservas confirmed do pedido e devolve estoque.
     *
     * @return quantas reservas foram liberadas
     */
    @Transactional
    int releaseConfirmed(Long idOrder) {
        // Encontra todas as reservas confirmed do pedido
        List<StockReservationEntity> confirmed = jpaRepository.findByIdOrder(idOrder).stream()
                .filter(r -> r.getStatus() == ReservationStatus.confirmed)
                .toList();

        // Libera e devolve estoque para cada uma
        for (StockReservationEntity r : confirmed) {
            productJpaRepository.incrementStock(r.getIdProduct(), r.getQuantity());
        }

        // Marca todas como released
        jpaRepository.updateStatusByOrder(idOrder, ReservationStatus.confirmed, ReservationStatus.released);

        return confirmed.size();
    }
}
