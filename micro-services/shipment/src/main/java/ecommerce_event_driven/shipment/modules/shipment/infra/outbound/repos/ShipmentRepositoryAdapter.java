package ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos;

import ecommerce_event_driven.shipment.modules.shipment.application.mappers.ShipmentMapper;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.repos.ShipmentRepositoryPort;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.Shipment;
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
}
