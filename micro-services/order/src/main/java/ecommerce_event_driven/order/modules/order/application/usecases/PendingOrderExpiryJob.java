package ecommerce_event_driven.order.modules.order.application.usecases;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;

/**
 * Job agendado que cancela pedidos pendentes apos 30 minutos sem pagamento.
 * Executa a cada 60 segundos (fixedDelay = 60000).
 */
@Component
public class PendingOrderExpiryJob {
    private static final Logger LOG = LoggerFactory.getLogger(PendingOrderExpiryJob.class);
    private static final long PENDING_EXPIRY_MINUTES = 30;

    private final OrderRepositoryPort orderRepo;
    private final OrderCancellationTransaction cancellation;

    public PendingOrderExpiryJob(OrderRepositoryPort orderRepo, OrderCancellationTransaction cancellation) {
        this.orderRepo = orderRepo;
        this.cancellation = cancellation;
    }

    @Scheduled(fixedDelay = 60000)  // 60 segundos
    public void checkExpiredOrders() {
        try {
            Instant threshold = Instant.now().minus(PENDING_EXPIRY_MINUTES, ChronoUnit.MINUTES);
            
            // Buscar todos os pedidos com status pending
            Pageable pageable = PageRequest.of(0, 100);
            Page<Order> pendingOrders = orderRepo.findByStatus(OrderStatus.pending, pageable);
            
            for (Order order : pendingOrders.getContent()) {
                if (order.createdAt() != null && order.createdAt().isBefore(threshold)) {
                    try {
                        LOG.info("Cancelando pedido {} por expiração de tempo de pagamento", order.id());
                        cancellation.cancel(order.id(), order.idCustomer(), 
                            "pagamento nao confirmado em 30 minutos", false);
                    } catch (Exception e) {
                        LOG.warn("Erro ao cancelar pedido {} por expiração: {}", order.id(), e.getMessage());
                    }
                }
            }
            
            LOG.debug("Job de expiração de pedidos executado");
        } catch (Exception e) {
            LOG.error("Erro ao executar job de expiração: {}", e.getMessage(), e);
        }
    }
}
