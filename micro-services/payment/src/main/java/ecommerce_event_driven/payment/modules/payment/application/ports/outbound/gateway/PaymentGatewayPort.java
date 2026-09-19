package ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;

public interface PaymentGatewayPort {
    JsonNode createPix(Long paymentId, BigDecimal amount, String email, String cpf, String idempotencyKey);

    JsonNode createCard(
            Long paymentId,
            BigDecimal amount,
            String token,
            String paymentMethodId,
            String issuerId,
            int installments,
            String email,
            String cpf,
            String idempotencyKey);

    JsonNode createPreference(
            Long paymentId,
            BigDecimal amount,
            String itemDescription,
            String idempotencyKey);

    JsonNode getPayment(String externalId);

    JsonNode refund(String externalId, BigDecimal amount, String idempotencyKey);

    JsonNode cancel(String externalId);

    JsonNode getPaymentMethods();
}
