package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.modules.order.application.ports.inbound.usecases.CancelOrderUseCase;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.shared.web.ConflictException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servico de cancelamento de pedido.
 * Delega a transacao e publicacao de eventos para OrderCancellationTransaction.
 */
@Service
public class CancelOrderService implements CancelOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderService.class);

    private final OrderCancellationTransaction cancellation;
    private final OrderRepositoryPort orders;

    public CancelOrderService(OrderCancellationTransaction cancellation, OrderRepositoryPort orders) {
        this.cancellation = cancellation;
        this.orders = orders;
    }

    /**
     * Cancela um pedido dentro de uma transacao com publicacao de eventos.
     * Se o pedido nao existe ou a transicao e invalida, lanca excecao.
     */
    public Result execute(Long idOrder, Long idCustomer, String reason, boolean isOwner) {
        try {
            cancellation.cancel(idOrder, idCustomer, reason, isOwner);
            log.info("Pedido {} cancelado por cliente {}: {}", idOrder, idCustomer, reason);
            return Result.CANCELLED;
        } catch (ConflictException e) {
            if ("ORDER_ALREADY_CANCELLED".equals(e.getCode())) {
                log.debug("Pedido {} ja foi cancelado", idOrder);
                return Result.NOT_APPLICABLE;
            }
            throw e;
        }
    }

    /**
     * Legado: execute com parametros minimos.
     * Esta sobrecarregacao e mantida por compatibilidade.
     */
    @Override
    public Result execute(Long idOrder, String reason) {
        // Nao temos informacoes do cliente aqui, entao retorna NOT_APPLICABLE
        log.warn("execute(idOrder, reason) chamado sem contexto de autenticacao");
        return Result.NOT_APPLICABLE;
    }
}
