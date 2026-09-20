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

    Optional<ShipmentEntity> findByIdAndDeletedAtIsNull(Long id);

    // Filtros opcionais viram metodos derivados, e nao um JPQL com condicao ligada/desligada:
    // uma lista vazia dentro de IN nao e SQL valido, e o adapter escolhe o metodo pelo filtro
    // que recebeu.

    Page<ShipmentEntity> findByIdUserAndDeletedAtIsNull(Long idUser, Pageable pageable);

    Page<ShipmentEntity> findByIdUserAndIdOrderAndDeletedAtIsNull(Long idUser, Long idOrder, Pageable pageable);

    Page<ShipmentEntity> findByIdUserAndStatusInAndDeletedAtIsNull(
            Long idUser, List<ShipmentStatus> statuses, Pageable pageable);

    Page<ShipmentEntity> findByIdUserAndStatusInAndIdOrderAndDeletedAtIsNull(
            Long idUser, List<ShipmentStatus> statuses, Long idOrder, Pageable pageable);

    Page<ShipmentEntity> findByDeletedAtIsNull(Pageable pageable);

    Page<ShipmentEntity> findByIdOrderAndDeletedAtIsNull(Long idOrder, Pageable pageable);

    Page<ShipmentEntity> findByStatusInAndDeletedAtIsNull(List<ShipmentStatus> statuses, Pageable pageable);

    Page<ShipmentEntity> findByStatusInAndIdOrderAndDeletedAtIsNull(
            List<ShipmentStatus> statuses, Long idOrder, Pageable pageable);

    @Query("SELECT s FROM ShipmentEntity s WHERE s.deletedAt IS NULL "
            + "AND s.status IN :statuses AND s.updatedAt < :timestamp")
    List<ShipmentEntity> findExpiredInTransit(
            @Param("statuses") List<ShipmentStatus> statuses,
            @Param("timestamp") Instant timestamp);
}
