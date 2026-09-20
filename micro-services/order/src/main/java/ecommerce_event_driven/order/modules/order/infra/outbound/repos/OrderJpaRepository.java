package ecommerce_event_driven.order.modules.order.infra.outbound.repos;

import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity.OrderEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** orders nao tem deleted_at: pedido e historico, nao se apaga. */
@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    Page<OrderEntity> findByIdCustomer(Long idCustomer, Pageable pageable);

    Optional<OrderEntity> findByIdAndIdCustomer(Long id, Long idCustomer);

    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Transicao condicional. Devolve 0 quando o pedido ja saiu do status de
     * origem, o que torna a transicao idempotente: evento repetido do Kafka
     * nao cancela um pedido que ja foi pago.
     */
    /** Atualiza so a projecao do pagamento, pelo mesmo motivo da projecao do envio. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderEntity o
               set o.paymentStatus = coalesce(:paymentStatus, o.paymentStatus),
                   o.refundedAmount = :refundedAmount
             where o.id = :idOrder
            """)
    int updatePaymentProjection(
            @Param("idOrder") Long idOrder,
            @Param("paymentStatus") String paymentStatus,
            @Param("refundedAmount") java.math.BigDecimal refundedAmount);

    /**
     * Atualiza so a projecao do envio.
     *
     * <p>Salvar o pedido inteiro aqui sobrescreveria o status com o valor lido antes do
     * evento: o estorno que chega em paralelo voltaria de refunded para cancelled.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderEntity o
               set o.shipmentId = :idShipment,
                   o.shipmentStatus = :shipmentStatus,
                   o.trackingCode = coalesce(:trackingCode, o.trackingCode)
             where o.id = :idOrder
            """)
    int updateShipmentProjection(
            @Param("idOrder") Long idOrder,
            @Param("idShipment") Long idShipment,
            @Param("shipmentStatus") String shipmentStatus,
            @Param("trackingCode") String trackingCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderEntity o
               set o.status = :to
             where o.id = :idOrder
               and o.status = :from
            """)
    int updateStatus(@Param("idOrder") Long idOrder,
            @Param("from") OrderStatus from,
            @Param("to") OrderStatus to);
}
