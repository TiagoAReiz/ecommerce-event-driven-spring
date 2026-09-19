package ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos;

import ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos.entity.ShipmentEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentJpaRepository extends JpaRepository<ShipmentEntity, Long> {

    /** Casa com shipment_order_uk: uma remessa ativa por pedido. */
    Optional<ShipmentEntity> findByIdOrderAndDeletedAtIsNull(Long idOrder);

    Optional<ShipmentEntity> findByTrackingCodeAndDeletedAtIsNull(String trackingCode);

    Page<ShipmentEntity> findByIdUserAndDeletedAtIsNull(Long idUser, Pageable pageable);

    Optional<ShipmentEntity> findByIdAndDeletedAtIsNull(Long id);
}
