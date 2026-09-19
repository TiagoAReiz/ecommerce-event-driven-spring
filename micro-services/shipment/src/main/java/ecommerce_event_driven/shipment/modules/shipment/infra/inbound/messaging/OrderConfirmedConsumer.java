package ecommerce_event_driven.shipment.modules.shipment.infra.inbound.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import ecommerce_event_driven.shipment.config.StoreOriginProperties;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.repos.ShipmentRepositoryPort;
import ecommerce_event_driven.shipment.modules.shipment.domain.events.ShipmentStatusChangedEvent;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.AddressSnapshot;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.Shipment;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.ShipmentStatus;
import ecommerce_event_driven.shipment.modules.shipment.infra.inbound.dtos.OrderConfirmedEventDto;
import ecommerce_event_driven.shipment.shared.client.UserServiceClient;
import ecommerce_event_driven.shipment.shared.messaging.InvalidEventException;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.events.ShipmentEventPublisherPort;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor do evento order.confirmed.
 * Cria um envio no status pending quando o pedido e confirmado.
 */
@Service
public class OrderConfirmedConsumer {
    private final ShipmentRepositoryPort repository;
    private final ShipmentEventPublisherPort eventPublisher;
    private final UserServiceClient userClient;
    private final StoreOriginProperties storeOrigin;

    public OrderConfirmedConsumer(
            ShipmentRepositoryPort repository,
            ShipmentEventPublisherPort eventPublisher,
            UserServiceClient userClient,
            StoreOriginProperties storeOrigin) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.userClient = userClient;
        this.storeOrigin = storeOrigin;
    }

    @KafkaListener(
            topics = "ecommerce.order.confirmed.v1",
            groupId = "shipment",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consume(OrderConfirmedEventDto event) throws JsonProcessingException {
        // Idempotencia: se ja existe envio para este pedido, ignora
        if (repository.findByIdOrder(event.orderId()).isPresent()) {
            return;
        }

        // Buscar endereco do comprador
        UserServiceClient.AddressDto address = userClient.getAddress(event.addressId(), event.customerId());

        // Criar snapshot do destino
        AddressSnapshot destination = AddressSnapshot.builder()
                .zipcode(address.zipcode())
                .country(address.country())
                .state(address.state())
                .city(address.city())
                .street(address.street())
                .number(address.number())
                .build();

        // Snapshot da origem (configuracao da loja)
        AddressSnapshot origin = AddressSnapshot.builder()
                .zipcode(storeOrigin.getZipcode())
                .country(storeOrigin.getCountry())
                .state(storeOrigin.getState())
                .city(storeOrigin.getCity())
                .street(storeOrigin.getStreet())
                .number(storeOrigin.getNumber())
                .build();

        Instant now = Instant.now();
        Shipment shipment = Shipment.builder()
                .idOrder(event.orderId())
                .idUser(event.customerId())
                .idAddressUser(event.addressId())
                .status(ShipmentStatus.pending)
                .freightTax(event.freightCost())
                .destination(destination)
                .origin(origin)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Shipment saved = repository.save(shipment);

        // Publicar evento de criacao
        eventPublisher.publishStatusChanged(new ShipmentStatusChangedEvent(
                saved.id(),
                saved.idOrder(),
                null,
                "pending",
                null,
                null,
                now));
    }
}
