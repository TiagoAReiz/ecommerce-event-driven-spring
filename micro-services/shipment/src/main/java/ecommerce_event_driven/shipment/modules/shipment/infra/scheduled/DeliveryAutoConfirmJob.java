package ecommerce_event_driven.shipment.modules.shipment.infra.scheduled;

import com.fasterxml.jackson.core.JsonProcessingException;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.repos.ShipmentRepositoryPort;
import ecommerce_event_driven.shipment.modules.shipment.domain.events.ShipmentStatusChangedEvent;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.Shipment;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.ShipmentStatus;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.events.ShipmentEventPublisherPort;
import ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos.ShipmentRepositoryAdapter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job que confirma automaticamente entregas 15 dias depois de in_transit ou out_for_delivery.
 * Executa diariamente as 04:00 UTC.
 */
@Component
public class DeliveryAutoConfirmJob {
    private static final long DELIVERY_CONFIRM_DAYS = 15;
    private static final Logger logger = LoggerFactory.getLogger(DeliveryAutoConfirmJob.class);

    private final ShipmentRepositoryPort repository;
    private final ShipmentRepositoryAdapter repositoryAdapter;
    private final ShipmentEventPublisherPort eventPublisher;

    public DeliveryAutoConfirmJob(
            ShipmentRepositoryPort repository,
            ShipmentRepositoryAdapter repositoryAdapter,
            ShipmentEventPublisherPort eventPublisher) {
        this.repository = repository;
        this.repositoryAdapter = repositoryAdapter;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void autoConfirmDeliveries() throws JsonProcessingException {
        Instant fifteenDaysAgo = Instant.now().minus(DELIVERY_CONFIRM_DAYS, ChronoUnit.DAYS);

        // Buscar envios em in_transit ou out_for_delivery que nao foram atualizados ha 15+ dias
        List<Shipment> expiredShipments = repositoryAdapter.findExpiredInTransit(fifteenDaysAgo);

        logger.info("DeliveryAutoConfirmJob: encontrados {} envios expirados", expiredShipments.size());

        // Processar e confirmar
        for (Shipment shipment : expiredShipments) {
            Instant now = Instant.now();
            String oldStatusName = shipment.status().name();

            Shipment updated = shipment.toBuilder()
                    .status(ShipmentStatus.delivered)
                    .updatedAt(now)
                    .build();
            repository.save(updated);

            // Publicar evento
            eventPublisher.publishStatusChanged(new ShipmentStatusChangedEvent(
                    updated.id(),
                    updated.idOrder(),
                    oldStatusName,
                    "delivered",
                    null,
                    null,
                    now));

            logger.debug("Auto-confirmed delivery for shipment {}", updated.id());
        }
    }
}
