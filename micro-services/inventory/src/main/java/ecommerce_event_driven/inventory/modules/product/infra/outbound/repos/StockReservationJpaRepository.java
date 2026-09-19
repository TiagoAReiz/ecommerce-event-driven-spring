package ecommerce_event_driven.inventory.modules.product.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.domain.models.ReservationStatus;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.StockReservationEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockReservationJpaRepository extends JpaRepository<StockReservationEntity, Long> {

    boolean existsByIdOrder(Long idOrder);

    /**
     * Quanto do produto esta segurado neste instante.
     *
     * <p>O filtro por expiresAt e o que substitui a rotina de limpeza: reserva
     * vencida continua na tabela, mas para de contar aqui. Bate no indice
     * parcial stock_reservation_active_idx.
     */
    @Query("""
            select coalesce(sum(r.quantity), 0)
              from StockReservationEntity r
             where r.idProduct = :idProduct
               and r.status = :status
               and r.expiresAt > :now
            """)
    int sumActiveQuantity(@Param("idProduct") Long idProduct,
            @Param("status") ReservationStatus status,
            @Param("now") Instant now);

    /**
     * Liberacao por pedido. O filtro por status torna a operacao idempotente e
     * protege reserva ja confirmada: pedido pago nao tem estoque devolvido aqui.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update StockReservationEntity r
               set r.status = :to
             where r.idOrder = :idOrder
               and r.status = :from
            """)
    int updateStatusByOrder(@Param("idOrder") Long idOrder,
            @Param("from") ReservationStatus from,
            @Param("to") ReservationStatus to);
}
