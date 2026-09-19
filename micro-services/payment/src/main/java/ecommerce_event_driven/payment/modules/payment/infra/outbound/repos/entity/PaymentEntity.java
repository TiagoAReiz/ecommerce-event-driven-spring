package ecommerce_event_driven.payment.modules.payment.infra.outbound.repos.entity;

import ecommerce_event_driven.payment.modules.payment.domain.models.PaymentStatus;
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
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Servico order, sem FK. */
    @Column(name = "id_order", nullable = false)
    private Long idOrder;

    @Column(name = "value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "payment_status")
    private PaymentStatus status;

    /** stripe, mercadopago... */
    @Column(name = "provider", length = 60)
    private String provider;

    /** Id da transacao no provider. */
    @Column(name = "external_id", length = 120)
    private String externalId;

    /** Sem isso, retentativa = cobranca dupla. */
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    /** Metodo de pagamento: pix, credit_card, checkout_pro */
    @Column(name = "method", length = 20)
    private String method;

    /** Detalhe do status do MP (ex: cc_rejected_insufficient_amount) */
    @Column(name = "status_detail", length = 120)
    private String statusDetail;

    /** QR Code em formato texto (PIX) */
    @Column(name = "qr_code")
    private String qrCode;

    /** QR Code em base64 (PIX) */
    @Column(name = "qr_code_base64")
    private String qrCodeBase64;

    /** URL do ticket do PIX */
    @Column(name = "ticket_url")
    private String ticketUrl;

    /** URL de checkout (Checkout Pro) */
    @Column(name = "init_point")
    private String initPoint;

    /** Data de expiracao do PIX ou preference */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Bandeira do cartao */
    @Column(name = "card_brand", length = 30)
    private String cardBrand;

    /** Ultimos 4 digitos do cartao */
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    /** Quantidade de parcelas do cartao */
    @Column(name = "installments")
    private Short installments;

    /** Valor total estornado */
    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundedAmount;

    /** Data de aprovacao no MP */
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
