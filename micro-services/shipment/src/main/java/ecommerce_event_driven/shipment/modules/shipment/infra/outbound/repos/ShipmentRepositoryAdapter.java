package ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos;

import ecommerce_event_driven.shipment.modules.shipment.application.mappers.ShipmentMapper;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.repos.ShipmentRepositoryPort;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.Shipment;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.ShipmentStatus;
import ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos.entity.ShipmentEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class ShipmentRepositoryAdapter implements ShipmentRepositoryPort {

    private final ShipmentJpaRepository jpaRepository;

    public ShipmentRepositoryAdapter(ShipmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Shipment save(Shipment shipment) {
        return ShipmentMapper.toDomain(jpaRepository.save(ShipmentMapper.toEntity(shipment)));
    }

    @Override
    public Optional<Shipment> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(ShipmentMapper::toDomain);
    }

    @Override
    public Optional<Shipment> findByIdOrder(Long idOrder) {
        return jpaRepository.findByIdOrderAndDeletedAtIsNull(idOrder).map(ShipmentMapper::toDomain);
    }

    @Override
    public Optional<Shipment> findByTrackingCode(String trackingCode) {
        return jpaRepository.findByTrackingCodeAndDeletedAtIsNull(trackingCode)
                .map(ShipmentMapper::toDomain);
    }

    @Override
    public Page<Shipment> findByIdUser(Long idUser, Pageable pageable) {
        return jpaRepository.findByIdUserAndDeletedAtIsNull(idUser, pageable)
                .map(ShipmentMapper::toDomain);
    }

    public Page<Shipment> findByIdUserWithFilters(Long idUser, List<String> statusFilters, Long orderId, Pageable pageable) {
        List<ShipmentStatus> statuses = statusFilters == null || statusFilters.isEmpty()
            ? List.of()
            : statusFilters.stream().map(ShipmentStatus::valueOf).toList();
        Page<ShipmentEntity> page;
        if (statuses.isEmpty() && orderId == null) {
            page = jpaRepository.findByIdUserAndDeletedAtIsNull(idUser, pageable);
        } else if (statuses.isEmpty()) {
            page = jpaRepository.findByIdUserAndIdOrderAndDeletedAtIsNull(idUser, orderId, pageable);
        } else if (orderId == null) {
            page = jpaRepository.findByIdUserAndStatusInAndDeletedAtIsNull(idUser, statuses, pageable);
        } else {
            page = jpaRepository.findByIdUserAndStatusInAndIdOrderAndDeletedAtIsNull(idUser, statuses, orderId, pageable);
        }
        return page.map(ShipmentMapper::toDomain);
    }

    public Page<Shipment> findAllWithFilters(List<String> statusFilters, Long orderId, Pageable pageable) {
        List<ShipmentStatus> statuses = statusFilters == null || statusFilters.isEmpty()
            ? List.of()
            : statusFilters.stream().map(ShipmentStatus::valueOf).toList();
        Page<ShipmentEntity> page;
        if (statuses.isEmpty() && orderId == null) {
            page = jpaRepository.findByDeletedAtIsNull(pageable);
        } else if (statuses.isEmpty()) {
            page = jpaRepository.findByIdOrderAndDeletedAtIsNull(orderId, pageable);
        } else if (orderId == null) {
            page = jpaRepository.findByStatusInAndDeletedAtIsNull(statuses, pageable);
        } else {
            page = jpaRepository.findByStatusInAndIdOrderAndDeletedAtIsNull(statuses, orderId, pageable);
        }
        return page.map(ShipmentMapper::toDomain);
    }

    public List<Shipment> findExpiredInTransit(Instant beforeTimestamp) {
        List<ShipmentStatus> statuses = List.of(ShipmentStatus.in_transit, ShipmentStatus.out_for_delivery);
        return jpaRepository.findExpiredInTransit(statuses, beforeTimestamp)
                .stream()
                .map(ShipmentMapper::toDomain)
                .toList();
    }
}
