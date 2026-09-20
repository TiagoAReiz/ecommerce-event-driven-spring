package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trata o evento payment.failed:
 * Atualiza a projecao payment_status, mas nao muda o status do pedido.
 * Projecao nao regride se ja estiver em captured.
 */
@Component
public class PaymentFailedEventHandler {
    private static final Logger log = LoggerFactory.getLogger(PaymentFailedEventHandler.class);

    private final OrderRepositoryPort orders;

    public PaymentFailedEventHandler(OrderRepositoryPort orders) {
        this.orders = orders;
    }

    @Transactional
    public void handle(Long orderId, String status, String statusDetail) throws InvalidEventException {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));

        // So atualizar projecao se pedido estiver em pending (permite nova tentativa)
        if (order.status() != OrderStatus.pending) {
            // Se ja foi pago ou cancelado, nao regedir projecao
            log.debug("payment.failed ignorado para pedido {} em estado {}", orderId, order.status());
            return;
        }

        // Atualizar apenas a projecao
        Order with_payment_status = order.toBuilder()
                .paymentStatus(status)
                .build();
        orders.save(with_payment_status);

        log.info("Projecao payment_status atualizada para pedido {}: {}", orderId, status);
    }
}
