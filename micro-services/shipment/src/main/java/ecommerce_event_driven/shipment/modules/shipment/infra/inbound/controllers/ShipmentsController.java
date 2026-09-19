package ecommerce_event_driven.shipment.modules.shipment.infra.inbound.controllers;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import ecommerce_event_driven.shipment.config.StoreOriginProperties;
import ecommerce_event_driven.shipment.modules.shipment.application.mappers.ShipmentMapper;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.repos.ShipmentRepositoryPort;
import ecommerce_event_driven.shipment.modules.shipment.domain.events.ShipmentStatusChangedEvent;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.AddressSnapshot;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.Shipment;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.ShipmentStatus;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.ShipmentStateMachine;
import ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos.ShipmentRepositoryAdapter;
import ecommerce_event_driven.shipment.shared.client.UserServiceClient;
import ecommerce_event_driven.shipment.shared.security.CurrentUser;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.events.ShipmentEventPublisherPort;
import ecommerce_event_driven.shipment.shared.web.BadRequestException;
import ecommerce_event_driven.shipment.shared.web.ConflictException;
import ecommerce_event_driven.shipment.shared.web.ForbiddenException;
import ecommerce_event_driven.shipment.shared.web.NotFoundException;
import ecommerce_event_driven.shipment.shared.web.PageResponse;
import ecommerce_event_driven.shipment.shared.web.PageMeta;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlador REST para gestao de envios.
 */
@RestController
@RequestMapping("/shipments")
public class ShipmentsController {
    private final ShipmentRepositoryPort repository;
    private final ShipmentRepositoryAdapter repositoryAdapter;
    private final ShipmentEventPublisherPort eventPublisher;
    private final UserServiceClient userClient;
    private final StoreOriginProperties storeOrigin;

    public ShipmentsController(
            ShipmentRepositoryPort repository,
            ShipmentRepositoryAdapter repositoryAdapter,
            ShipmentEventPublisherPort eventPublisher,
            UserServiceClient userClient,
            StoreOriginProperties storeOrigin) {
        this.repository = repository;
        this.repositoryAdapter = repositoryAdapter;
        this.eventPublisher = eventPublisher;
        this.userClient = userClient;
        this.storeOrigin = storeOrigin;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_shipments:read')")
    public ResponseEntity<PageResponse<ShipmentResponse>> list(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) Long orderId,
            Pageable pageable,
            Jwt jwt) {
        CurrentUser user = new CurrentUser(jwt);
        Long userId = user.getId();
        if (userId == null) {
            throw new ForbiddenException("Acesso negado");
        }
        Page<Shipment> shipments = repositoryAdapter.findByIdUserWithFilters(userId, status, orderId, pageable);
        Page<ShipmentResponse> responses = shipments.map(this::toResponse);
        return ResponseEntity.ok(new PageResponse<>(responses.getContent(),
            new PageMeta(responses.getNumber(), responses.getSize(),
                         responses.getTotalElements(), responses.getTotalPages())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_shipments:read')")
    public ResponseEntity<ShipmentDetailedResponse> get(
            @PathVariable Long id,
            Jwt jwt) {
        CurrentUser user = new CurrentUser(jwt);
        Long userId = user.getId();

        Shipment shipment = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Envio nao encontrado"));

        // Verificar se eh o comprador ou o owner
        if (!shipment.idUser().equals(userId) && !user.hasRole("owner")) {
            throw new NotFoundException("Envio nao encontrado");
        }

        return ResponseEntity.ok(toDetailedResponse(shipment));
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAuthority('SCOPE_sales:read')")
    public ResponseEntity<PageResponse<ShipmentResponse>> manage(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) Long orderId,
            Pageable pageable,
            Jwt jwt) {
        CurrentUser user = new CurrentUser(jwt);
        if (!user.hasRole("owner")) {
            throw new ForbiddenException("Apenas o dono pode acessar");
        }

        Page<Shipment> shipments = repositoryAdapter.findAllWithFilters(status, orderId, pageable);
        Page<ShipmentResponse> responses = shipments.map(this::toResponse);
        return ResponseEntity.ok(new PageResponse<>(responses.getContent(),
            new PageMeta(responses.getNumber(), responses.getSize(),
                         responses.getTotalElements(), responses.getTotalPages())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_shipments:write')")
    @Transactional
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateShipmentRequest request,
            Jwt jwt) throws JsonProcessingException {
        CurrentUser user = new CurrentUser(jwt);
        if (!user.hasRole("owner")) {
            throw new ForbiddenException("Apenas o dono pode atualizar");
        }

        if (request.status() == null) {
            throw new BadRequestException("Status eh obrigatorio");
        }

        // Validar que nao e delivered
        if ("delivered".equals(request.status())) {
            throw new ForbiddenException("Use POST /shipments/{id}/confirm-delivery");
        }

        // Validar tracking code
        if (request.trackingCode() != null && request.trackingCode().length() > 60) {
            throw new BadRequestException("Tracking code deve ter no maximo 60 caracteres");
        }

        Shipment shipment = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Envio nao encontrado"));

        ShipmentStatus newStatus = ShipmentStatus.valueOf(request.status());
        String oldStatusName = shipment.status().name();

        // Validar transicao
        try {
            ShipmentStateMachine.validateTransition(oldStatusName, request.status());
        } catch (IllegalArgumentException e) {
            throw new ConflictException(e.getMessage());
        }

        Instant now = Instant.now();
        Shipment updated = shipment.toBuilder()
            .status(newStatus)
            .trackingCode(request.trackingCode() != null ? request.trackingCode() : shipment.trackingCode())
            .updatedAt(now)
            .build();

        repository.save(updated);

        // Publicar evento
        eventPublisher.publishStatusChanged(new ShipmentStatusChangedEvent(
            updated.id(),
            updated.idOrder(),
            oldStatusName,
            request.status(),
            null,
            null,
            now));

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/confirm-delivery")
    @PreAuthorize("hasAuthority('SCOPE_shipments:write')")
    @Transactional
    public ResponseEntity<Void> confirmDelivery(
            @PathVariable Long id,
            Jwt jwt) throws JsonProcessingException {
        CurrentUser user = new CurrentUser(jwt);
        Long userId = user.getId();

        Shipment shipment = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Envio nao encontrado"));

        // Verificar se eh o comprador
        if (!shipment.idUser().equals(userId)) {
            throw new NotFoundException("Envio nao encontrado");
        }

        // Se ja esta delivered, retorna ok (idempotente)
        if (shipment.status() == ShipmentStatus.delivered) {
            return ResponseEntity.ok().build();
        }

        // Verificar se pode transicionar para delivered
        if (shipment.status() != ShipmentStatus.in_transit &&
            shipment.status() != ShipmentStatus.out_for_delivery) {
            throw new ConflictException("Status nao permite confirmacao de entrega");
        }

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

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_shipments:write')")
    @Transactional
    public ResponseEntity<Void> cancel(
            @PathVariable Long id,
            @RequestBody CancelShipmentRequest request,
            Jwt jwt) throws JsonProcessingException {
        CurrentUser user = new CurrentUser(jwt);
        if (!user.hasRole("owner")) {
            throw new ForbiddenException("Apenas o dono pode cancelar");
        }

        if (request.reason() != null && request.reason().length() > 500) {
            throw new BadRequestException("Motivo deve ter no maximo 500 caracteres");
        }

        Shipment shipment = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Envio nao encontrado"));

        // Validar que eh pending ou ready_to_ship
        if (shipment.status() != ShipmentStatus.pending &&
            shipment.status() != ShipmentStatus.ready_to_ship) {
            throw new ConflictException("Apenas envios em pending ou ready_to_ship podem ser cancelados");
        }

        // Se ja esta cancelled, retorna 422
        if (shipment.status() == ShipmentStatus.cancelled) {
            throw new ConflictException("Envio ja esta cancelado");
        }

        Instant now = Instant.now();
        String oldStatusName = shipment.status().name();

        Shipment updated = shipment.toBuilder()
            .status(ShipmentStatus.cancelled)
            .cancelReason(request.reason())
            .updatedAt(now)
            .build();

        repository.save(updated);

        // Publicar evento
        eventPublisher.publishStatusChanged(new ShipmentStatusChangedEvent(
            updated.id(),
            updated.idOrder(),
            oldStatusName,
            "cancelled",
            null,
            null,
            now));

        return ResponseEntity.ok().build();
    }

    @PostMapping("/internal/shipments")
    @PreAuthorize("hasAuthority('SCOPE_internal:hydrate')")
    @Transactional
    public ResponseEntity<ShipmentResponse> createInternal(
            @RequestBody CreateInternalShipmentRequest request) throws JsonProcessingException {
        // Buscar endereco
        UserServiceClient.AddressDto address = userClient.getAddress(request.idAddressUser(), request.idUser());

        // Criar envio
        AddressSnapshot destination = AddressSnapshot.builder()
                .zipcode(address.zipcode())
                .country(address.country())
                .state(address.state())
                .city(address.city())
                .street(address.street())
                .number(address.number())
                .build();

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
                .idOrder(request.idOrder())
                .idUser(request.idUser())
                .idAddressUser(request.idAddressUser())
                .status(ShipmentStatus.pending)
                .freightTax(request.freightTax())
                .destination(destination)
                .origin(origin)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Shipment saved = repository.save(shipment);

        // Publicar evento
        eventPublisher.publishStatusChanged(new ShipmentStatusChangedEvent(
                saved.id(),
                saved.idOrder(),
                null,
                "pending",
                null,
                null,
                now));

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        return new ShipmentResponse(
            shipment.id(),
            shipment.idOrder(),
            shipment.status().name(),
            shipment.freightTax(),
            shipment.trackingCode(),
            new ShipmentResponse.Address(
                shipment.destination().city(),
                shipment.destination().state(),
                shipment.destination().zipcode()),
            shipment.updatedAt());
    }

    private ShipmentDetailedResponse toDetailedResponse(Shipment shipment) {
        return new ShipmentDetailedResponse(
            shipment.id(),
            shipment.idOrder(),
            shipment.status().name(),
            shipment.freightTax(),
            shipment.trackingCode(),
            new ShipmentDetailedResponse.AddressFull(
                shipment.destination().zipcode(),
                shipment.destination().country(),
                shipment.destination().state(),
                shipment.destination().city(),
                shipment.destination().street(),
                shipment.destination().number()),
            new ShipmentDetailedResponse.AddressFull(
                shipment.origin().zipcode(),
                shipment.origin().country(),
                shipment.origin().state(),
                shipment.origin().city(),
                shipment.origin().street(),
                shipment.origin().number()),
            shipment.updatedAt());
    }

    // DTOs
    public record UpdateShipmentRequest(String status, String trackingCode) {}
    public record CancelShipmentRequest(String reason) {}
    public record CreateInternalShipmentRequest(
            Long idOrder, Long idUser, Long idAddressUser, BigDecimal freightTax) {}

    public record ShipmentResponse(
            Long id,
            Long idOrder,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal freightTax,
            String trackingCode,
            Address destination,
            Instant updatedAt) {
        public record Address(String city, String state, String zipcode) {}
    }

    public record ShipmentDetailedResponse(
            Long id,
            Long idOrder,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal freightTax,
            String trackingCode,
            AddressFull destination,
            AddressFull origin,
            Instant updatedAt) {
        public record AddressFull(String zipcode, String country, String state,
                                   String city, String street, String number) {}
    }
}
