package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.messaging.StockEventPublisherPort;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.StockReservationRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.StockReservationJpaRepository;
import ecommerce_event_driven.inventory.modules.product.domain.models.ReservationStatus;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transacao separada para registrar falha de commit.
 *
 * <p>Executada apos o rollback de StockCommitTransaction. Libera as held
 * do pedido e publica stock.commit.failed na outbox.
 */
@Component
class CommitFailureTransaction {

    private final StockReservationRepositoryPort reservations;
    private final StockReservationJpaRepository jpaRepository;
    private final StockEventPublisherPort publisher;

    CommitFailureTransaction(
            StockReservationRepositoryPort reservations,
            StockReservationJpaRepository jpaRepository,
            StockEventPublisherPort publisher) {
        this.reservations = reservations;
        this.jpaRepository = jpaRepository;
        this.publisher = publisher;
    }

    /**
     * Libera as held do pedido e publica stock.commit.failed.
     *
     * @param idProduct primeiro produto que falhou (para o evento)
     */
    @Transactional
    void recordFailure(Long idOrder, Long idProduct, String reason) {
        // Libera as held do pedido
        reservations.releaseHeldByOrder(idOrder);

        // Se nao temos idProduct, procura o primeiro held do pedido
        if (idProduct == null) {
            List<Object[]> held = jpaRepository.findByOrderAndStatus(idOrder, ReservationStatus.held);
            if (!held.isEmpty()) {
                idProduct = (Long) held.get(0)[0];
            }
        }

        // Publica falha (mesmo sem idProduct, usar 0)
        publisher.publishStockCommitFailed(idOrder, idProduct != null ? idProduct : 0L, reason);
    }
}
