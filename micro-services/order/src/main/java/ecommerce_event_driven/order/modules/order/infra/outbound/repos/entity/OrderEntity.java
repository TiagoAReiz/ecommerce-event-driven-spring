package ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity;

import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Servico user, sem FK. */
    @Column(name = "id_customer", nullable = false)
    private Long idCustomer;

    /** Endereco de entrega, servico user, sem FK. */
    @Column(name = "id_address", nullable = false)
    private Long idAddress;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "order_status")
    private OrderStatus status;

    @Column(name = "items_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal itemsCost;

    @Column(name = "freight_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal freightCost;

    @Column(name = "total_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCost;

    /** Projecoes de pagamento, envio e reserva de estoque. */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "shipment_status")
    private String shipmentStatus;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(name = "stock_reservation", nullable = false)
    private String stockReservation = "pending";

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
