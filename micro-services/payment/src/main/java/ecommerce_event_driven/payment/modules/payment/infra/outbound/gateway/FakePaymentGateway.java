package ecommerce_event_driven.payment.modules.payment.infra.outbound.gateway;

import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class FakePaymentGateway implements PaymentGatewayPort {
    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);
    private final JsonMapper json = new JsonMapper();
    private static boolean booted = false;

    public FakePaymentGateway() {
        if (!booted) {
            log.warn("modo fake do Mercado Pago");
            booted = true;
        }
    }

    @Override
    public JsonNode createPix(
            Long paymentId, BigDecimal amount, String email, String cpf, String idempotencyKey) {
        ObjectNode response = json.createObjectNode();
        String externalId = "fake-pix-" + UUID.randomUUID();
        response.put("id", externalId);
        response.put("status", "pending");
        response.put("status_detail", "pending_waiting_transfer");
        response.put("transaction_amount", amount.doubleValue());

        ObjectNode pointOfInteraction = response.putObject("point_of_interaction");
        ObjectNode transactionData = pointOfInteraction.putObject("transaction_data");
        transactionData.put("qr_code", "00020126580014br.gov.bcb.pix0136" + externalId);
        transactionData.put("qr_code_base64", "iVBORw0KGgoAAAANSUhEUgAAAZQAAAGU");
        transactionData.put("ticket_url", "https://www.mercadopago.com.br/payments/" + externalId);

        return response;
    }

    @Override
    public JsonNode createCard(
            Long paymentId,
            BigDecimal amount,
            String token,
            String paymentMethodId,
            String issuerId,
            int installments,
            String email,
            String cpf,
            String idempotencyKey) {
        ObjectNode response = json.createObjectNode();
        String externalId = "fake-card-" + UUID.randomUUID();
        response.put("id", externalId);
        response.put("status", "approved");
        response.put("status_detail", "accredited");
        response.put("transaction_amount", amount.doubleValue());

        ObjectNode card = response.putObject("card");
        card.put("last_four_digits", "4321");
        card.put("brand", paymentMethodId);

        return response;
    }

    @Override
    public JsonNode createPreference(
            Long paymentId, BigDecimal amount, String itemDescription, String idempotencyKey) {
        ObjectNode response = json.createObjectNode();
        String preferenceId = "fake-pref-" + UUID.randomUUID();
        response.put("id", preferenceId);
        response.put("init_point", "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=" + preferenceId);
        response.put("sandbox_init_point",
                "https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=" + preferenceId);

        return response;
    }

    @Override
    public JsonNode getPayment(String externalId) {
        ObjectNode response = json.createObjectNode();
        response.put("id", externalId);
        response.put("status", "pending");
        response.put("status_detail", "pending_waiting_transfer");
        response.put("date_approved", Instant.now().toString());

        return response;
    }

    @Override
    public JsonNode refund(String externalId, BigDecimal amount, String idempotencyKey) {
        ObjectNode response = json.createObjectNode();
        String refundId = "fake-refund-" + UUID.randomUUID();
        response.put("id", refundId);
        response.put("status", "succeeded");
        response.put("amount", amount.doubleValue());

        return response;
    }

    @Override
    public JsonNode cancel(String externalId) {
        ObjectNode response = json.createObjectNode();
        response.put("id", externalId);
        response.put("status", "cancelled");

        return response;
    }

    @Override
    public JsonNode getPaymentMethods() {
        ObjectNode response = json.createObjectNode();
        var methods = response.putArray("payment_methods");

        ObjectNode pix = methods.addObject();
        pix.put("id", "pix");
        pix.put("name", "PIX");
        pix.put("type", "bank_transfer");

        ObjectNode card = methods.addObject();
        card.put("id", "master");
        card.put("name", "Mastercard");
        card.put("type", "credit_card");

        return response;
    }
}
