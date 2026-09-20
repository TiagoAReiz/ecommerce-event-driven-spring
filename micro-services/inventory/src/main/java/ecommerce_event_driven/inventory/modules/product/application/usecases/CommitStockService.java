package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.CommitStockUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.messaging.StockEventPublisherPort;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.StockReservationRepositoryPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordena a baixa de estoque: transacao de commit com rollback controlado.
 *
 * <p>Com outbox: a publicacao de stock.committed entra na mesma transacao
 * do commit. A publicacao de stock.commit.failed vai numa transacao propria
 * apos o rollback, com limpeza de held.
 */
@Service
public class CommitStockService implements CommitStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(CommitStockService.class);

    private final StockCommitTransaction stockCommit;
    private final CommitFailureTransaction commitFailure;
    private final StockEventPublisherPort publisher;

    public CommitStockService(
            StockCommitTransaction stockCommit,
            CommitFailureTransaction commitFailure,
            StockEventPublisherPort publisher) {
        this.stockCommit = stockCommit;
        this.commitFailure = commitFailure;
        this.publisher = publisher;
    }

    /**
     * Tenta baixar estoque do pedido.
     *
     * @return resultado do commit
     */
    @Override
    public Result execute(Long idOrder, List<Item> items) {
        CommitResult result = stockCommit.commit(idOrder, items);

        switch (result) {
            case OK:
                // Publica stock.committed na mesma transacao do commit
                publisher.publishStockCommitted(idOrder);
                log.info("Estoque commitado do pedido {}", idOrder);
                return Result.COMMITTED;

            case ALREADY_COMMITTED:
                // Reentrega: ja foi commitado, nao publica de novo
                log.debug("Pedido {} ja tinha commit, ignorado", idOrder);
                return Result.ALREADY_COMMITTED;

            case COMMIT_FAILED_RESERVATION_RELEASED:
                // Reserva ja foi released: libera e publica falha
                commitFailure.recordFailure(idOrder, null, "RESERVATION_RELEASED");
                log.info("Estoque commit falhou: reserva ja liberada no pedido {}", idOrder);
                return Result.COMMIT_FAILED;

            case COMMIT_FAILED_EXPIRED_WITHOUT_STOCK:
                // Reserva venceu e estoque acabou: libera held e publica falha
                commitFailure.recordFailure(idOrder, null, "EXPIRED_WITHOUT_STOCK");
                log.info("Estoque commit falhou: reserva vencida sem estoque no pedido {}", idOrder);
                return Result.COMMIT_FAILED;

            case COMMIT_FAILED_PRODUCT_NOT_FOUND:
                // Produto nao encontrado: libera held e publica falha
                commitFailure.recordFailure(idOrder, null, "EXPIRED_WITHOUT_STOCK");
                log.info("Estoque commit falhou: produto nao encontrado no pedido {}", idOrder);
                return Result.COMMIT_FAILED;

            default:
                throw new IllegalStateException("Resultado desconhecido: " + result);
        }
    }

    enum CommitResult {
        OK,
        ALREADY_COMMITTED,
        COMMIT_FAILED_RESERVATION_RELEASED,
        COMMIT_FAILED_EXPIRED_WITHOUT_STOCK,
        COMMIT_FAILED_PRODUCT_NOT_FOUND
    }
}
