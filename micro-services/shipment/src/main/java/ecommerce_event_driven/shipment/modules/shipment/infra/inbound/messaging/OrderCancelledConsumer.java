package ecommerce_event_driven.shipment.modules.shipment.infra.inbound.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.repos.ShipmentRepositoryPort;
import ecommerce_event_driven.shipment.modules.shipment.domain.events.ShipmentStatusChangedEvent;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.Shipment;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.ShipmentStatus;
import ecommerce_event_driven.shipment.modules.shipment.infra.inbound.dtos.OrderCancelledEventDto;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.events.ShipmentEventPublisherPort;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor do evento order.cancelled.
 * Cancela o envio se estiver em pending ou ready_to_ship.
 */
@Service
public class OrderCancelledConsumer {
    private final ShipmentRepositoryPort repository;
    private final ShipmentEventPublisherPort eventPublisher;

    public OrderCancelledConsumer(
            ShipmentRepositoryPort repository,
            ShipmentEventPublisherPort eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(
            topics = "ecommerce.order.cancelled.v1",
            groupId = "shipment",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consume(OrderCancelledEventDto event) throws JsonProcessingException {
        Shipment shipment = repository.findByIdOrder(event.orderId()).orElse(null);
        if (shipment == null) {
            // Nenhum envio ainda, ignora
            return;
        }

        ShipmentStatus status = shipment.status();
        if (status == ShipmentStatus.pending || status == ShipmentStatus.ready_to_ship) {
            // Pode cancelar
            Shipment updated = shipment.toBuilder()
                    .status(ShipmentStatus.cancelled)
                    .updatedAt(Instant.now())
                    .build();
            repository.save(updated);

            // Publicar evento de cancelamento
            Instant now = Instant.now();
            eventPublisher.publishStatusChanged(new ShipmentStatusChangedEvent(
                    updated.id(),
                    updated.idOrder(),
                    status.name(),
                    "cancelled",
                    null,
                    null,
                    now));
        } else if (status == ShipmentStatus.in_transit || status == ShipmentStatus.out_for_delivery) {
            // Log WARN: mercadoria ja saiu, e devolucao, nao muda nada
            System.out.println("WARN: Pedido " + event.orderId() + " cancelado mas envio ja em " + status);
        }
        // Outros status (delivered, returned, cancelled): ignora
    }
}
