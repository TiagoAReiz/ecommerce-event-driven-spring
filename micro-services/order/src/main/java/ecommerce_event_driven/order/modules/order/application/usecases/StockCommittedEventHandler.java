package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trata o evento stock.committed: transicao paid -> processing
 * e publica order.confirmed dentro da mesma transacao.
 */
@Component
public class StockCommittedEventHandler {
    private static final Logger log = LoggerFactory.getLogger(StockCommittedEventHandler.class);

    private final OrderRepositoryPort orders;
    private final OrderEventPublisherPort publisher;

    public StockCommittedEventHandler(OrderRepositoryPort orders, OrderEventPublisherPort publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public void handle(Long orderId) throws InvalidEventException {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));

        // Decidir pelo estado atual: so avanca se estiver em paid
        if (order.status() != OrderStatus.paid) {
            if (order.status().ordinal() > OrderStatus.paid.ordinal()) {
                // Estado a frente: ignorar (ja processou stock.committed)
                log.debug("stock.committed ignorado: pedido {} ja em estado {}", orderId, order.status());
            }
            return;
        }

        // Transicionar paid -> processing
        boolean updated = orders.updateStatus(orderId, OrderStatus.paid, OrderStatus.processing);
        if (!updated) {
            // Outro evento alterou o status: tratar como concorrencia normal
            log.debug("stock.committed nao atualizou pedido {}: ja em estado diferente de paid", orderId);
            return;
        }

        // Recarregar para ter os dados completos
        Order updated_order = orders.findById(orderId)
                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado apos atualizacao"));

        // Publicar order.confirmed
        publisher.publishOrderConfirmed(updated_order);

        log.info("Pedido {} avancou de paid para processing (stock.committed)", orderId);
    }
}
