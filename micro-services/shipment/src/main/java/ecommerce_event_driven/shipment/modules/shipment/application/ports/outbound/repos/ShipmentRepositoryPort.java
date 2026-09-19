package ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.repos;

import ecommerce_event_driven.shipment.modules.shipment.domain.models.Shipment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 */
public interface ShipmentRepositoryPort {

    Shipment save(Shipment shipment);

    Optional<Shipment> findById(Long id);

    /** Uma remessa ativa por pedido. */
    Optional<Shipment> findByIdOrder(Long idOrder);

    Optional<Shipment> findByTrackingCode(String trackingCode);

    Page<Shipment> findByIdUser(Long idUser, Pageable pageable);
}
