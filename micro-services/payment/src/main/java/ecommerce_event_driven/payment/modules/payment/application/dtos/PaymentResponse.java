package ecommerce_event_driven.payment.modules.payment.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pagamento como a API devolve. Nunca expoe a chave de idempotencia: ela e segredo do
 * cliente que criou a cobranca, e com ela alguem repetiria a criacao.
 */
public record PaymentResponse(
        Long id,
        Long idOrder,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value,
        String status,
        String provider,
        String externalId,
        String method,
        String statusDetail,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> detail,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal refundedAmount,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment p) {
        // O detalhe muda por modalidade: QR no PIX, link no Checkout Pro, final do cartao.
        Map<String, Object> detail = new LinkedHashMap<>();
        put(detail, "qrCode", p.qrCode());
        put(detail, "qrCodeBase64", p.qrCodeBase64());
        put(detail, "ticketUrl", p.ticketUrl());
        put(detail, "initPoint", p.initPoint());
        put(detail, "expiresAt", p.expiresAt());
        put(detail, "brand", p.cardBrand());
        put(detail, "last4", p.cardLast4());
        put(detail, "installments", p.installments());
        return new PaymentResponse(
                p.id(),
                p.idOrder(),
                p.value(),
                p.status() == null ? null : p.status().name(),
                p.provider(),
                p.externalId(),
                p.method(),
                p.statusDetail(),
                detail,
                p.refundedAmount(),
                p.approvedAt(),
                p.createdAt(),
                p.updatedAt());
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
