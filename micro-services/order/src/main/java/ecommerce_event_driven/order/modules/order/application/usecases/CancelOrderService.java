package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.modules.order.application.ports.inbound.usecases.CancelOrderUseCase;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CancelOrderService implements CancelOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderService.class);

    private final OrderCancellationTransaction cancellation;
    private final OrderRepositoryPort orders;
    private final OrderEventPublisherPort publisher;

    public CancelOrderService(OrderCancellationTransaction cancellation,
            OrderRepositoryPort orders,
            OrderEventPublisherPort publisher) {
        this.cancellation = cancellation;
        this.orders = orders;
        this.publisher = publisher;
    }

    /**
     * Publica so depois do commit. E so publica se a transicao realmente
     * aconteceu: evento repetido nao vira um segundo OrderCancelled.
     */
    @Override
    public Result execute(Long idOrder, String reason) {
        if (!cancellation.cancelIfPending(idOrder)) {
            log.debug("Pedido {} nao estava em pending, cancelamento ignorado", idOrder);
            return Result.NOT_APPLICABLE;
        }

        Optional<Order> cancelled = orders.findById(idOrder);
        Long idCustomer = cancelled.map(Order::idCustomer).orElse(null);

        log.info("Pedido {} cancelado: {}", idOrder, reason);
        publisher.publishOrderCancelled(idOrder, idCustomer, reason);
        return Result.CANCELLED;
    }
}
