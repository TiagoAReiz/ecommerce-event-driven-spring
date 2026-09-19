package ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos;

import ecommerce_event_driven.shipment.modules.shipment.domain.models.ShipmentStatus;
import ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos.entity.ShipmentEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentJpaRepository extends JpaRepository<ShipmentEntity, Long> {

    /** Casa com shipment_order_uk: uma remessa ativa por pedido. */
    Optional<ShipmentEntity> findByIdOrderAndDeletedAtIsNull(Long idOrder);

    Optional<ShipmentEntity> findByTrackingCodeAndDeletedAtIsNull(String trackingCode);

    Page<ShipmentEntity> findByIdUserAndDeletedAtIsNull(Long idUser, Pageable pageable);

    Optional<ShipmentEntity> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT s FROM ShipmentEntity s WHERE s.deletedAt IS NULL AND s.idUser = :idUser " +
           "AND (:#{#statuses.isEmpty() ? 'true' : 'false'} OR s.status IN :statuses) " +
           "AND (:orderId IS NULL OR s.idOrder = :orderId)")
    Page<ShipmentEntity> findByIdUserWithFilters(
            @Param("idUser") Long idUser,
            @Param("statuses") List<ShipmentStatus> statuses,
            @Param("orderId") Long orderId,
            Pageable pageable);

    @Query("SELECT s FROM ShipmentEntity s WHERE s.deletedAt IS NULL " +
           "AND (:#{#statuses.isEmpty() ? 'true' : 'false'} OR s.status IN :statuses) " +
           "AND (:orderId IS NULL OR s.idOrder = :orderId)")
    Page<ShipmentEntity> findAllWithFilters(
            @Param("statuses") List<ShipmentStatus> statuses,
            @Param("orderId") Long orderId,
            Pageable pageable);

    @Query("SELECT s FROM ShipmentEntity s WHERE s.deletedAt IS NULL " +
           "AND s.status IN :statuses AND s.updatedAt < :timestamp")
    List<ShipmentEntity> findExpiredInTransit(
            @Param("statuses") List<ShipmentStatus> statuses,
            @Param("timestamp") Instant timestamp);
}
