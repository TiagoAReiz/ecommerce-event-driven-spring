package ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos.entity;

import ecommerce_event_driven.shipment.modules.shipment.domain.models.ShipmentStatus;
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
@Table(name = "shipment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Servico order, sem FK. */
    @Column(name = "id_order", nullable = false)
    private Long idOrder;

    /** Servico user, sem FK. */
    @Column(name = "id_user", nullable = false)
    private Long idUser;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "shipment_status")
    private ShipmentStatus status;

    @Column(name = "freight_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal freightTax;

    @Column(name = "tracking_code", length = 60)
    private String trackingCode;

    // destino: [snapshot] completo, endereco no user e mutavel

    @Column(name = "id_address_user", nullable = false)
    private Long idAddressUser;

    @Column(name = "to_zipcode", nullable = false, length = 20)
    private String toZipcode;

    @Column(name = "to_country", nullable = false, length = 60)
    private String toCountry;

    @Column(name = "to_state", nullable = false, length = 60)
    private String toState;

    @Column(name = "to_city", nullable = false, length = 120)
    private String toCity;

    @Column(name = "to_street", nullable = false, length = 200)
    private String toStreet;

    @Column(name = "to_number", length = 20)
    private String toNumber;

    // origem: [snapshot] completo

    @Column(name = "from_zipcode", nullable = false, length = 20)
    private String fromZipcode;

    @Column(name = "from_country", nullable = false, length = 60)
    private String fromCountry;

    @Column(name = "from_state", nullable = false, length = 60)
    private String fromState;

    @Column(name = "from_city", nullable = false, length = 120)
    private String fromCity;

    @Column(name = "from_street", nullable = false, length = 200)
    private String fromStreet;

    @Column(name = "from_number", length = 20)
    private String fromNumber;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;
}
