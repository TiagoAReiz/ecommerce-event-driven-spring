package ecommerce_event_driven.payment.modules.payment.domain.models;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Payment(
        Long id,
        Long idOrder,
        BigDecimal value,
        PaymentStatus status,
        /** stripe, mercadopago... */
        String provider,
        /** Id da transacao no provider. */
        String externalId,
        /** Sem isso, retentativa vira cobranca dupla. */
        String idempotencyKey,
        /** Metodo de pagamento: pix, credit_card, checkout_pro */
        String method,
        /** Detalhe do status do MP (ex: cc_rejected_insufficient_amount) */
        String statusDetail,
        /** QR Code em formato texto (PIX) */
        String qrCode,
        /** QR Code em base64 (PIX) */
        String qrCodeBase64,
        /** URL do ticket do PIX */
        String ticketUrl,
        /** URL de checkout (Checkout Pro) */
        String initPoint,
        /** Data de expiracao do PIX ou preference */
        Instant expiresAt,
        /** Bandeira do cartao */
        String cardBrand,
        /** Ultimos 4 digitos do cartao */
        String cardLast4,
        /** Quantidade de parcelas do cartao */
        Short installments,
        /** Valor total estornado */
        BigDecimal refundedAmount,
        /** Data de aprovacao no MP */
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
