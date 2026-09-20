package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.ReleaseReservationUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.StockReservationRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseReservationService implements ReleaseReservationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReleaseReservationService.class);

    private final StockReservationRepositoryPort reservations;
    private final ReleaseConfirmedStockTransaction releaseConfirmedStock;

    public ReleaseReservationService(
            StockReservationRepositoryPort reservations,
            ReleaseConfirmedStockTransaction releaseConfirmedStock) {
        this.reservations = reservations;
        this.releaseConfirmedStock = releaseConfirmedStock;
    }

    /**
     * Sem I/O depois do UPDATE, entao nao precisa da divisao em dois beans que
     * a reserva tem: o @Transactional pode ficar aqui mesmo.
     */
    @Override
    @Transactional
    public Result execute(Long idOrder) {
        int released = reservations.releaseHeldByOrder(idOrder);
        if (released == 0) {
            log.debug("Pedido {} nao tinha reserva ativa, nada a liberar", idOrder);
            return Result.NOTHING_TO_RELEASE;
        }

        log.info("Reservas liberadas do pedido {}: {}", idOrder, released);
        return Result.RELEASED;
    }

    /**
     * Libera reservas confirmed (pedido cancelado apos pagamento).
     * Devolve o estoque que foi debitado.
     */
    @Transactional
    public Result releaseConfirmedStock(Long idOrder) {
        int released = releaseConfirmedStock.releaseConfirmed(idOrder);
        if (released == 0) {
            log.debug("Pedido {} nao tinha reserva confirmed, nada a liberar", idOrder);
            return Result.NOTHING_TO_RELEASE;
        }

        log.info("Estoque devolvido do pedido {} (confirmed -> released): {} items", idOrder, released);
        return Result.RELEASED;
    }
}
