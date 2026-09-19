package ecommerce_event_driven.payment.modules.payment.infra.outbound.gateway;

import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class MercadoPagoClient implements PaymentGatewayPort {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);
    private final RestClient restClient;
    private final JsonMapper json;
    private final String baseUrl;
    private final String accessToken;

    public MercadoPagoClient(
            @Value("${app.mercadopago.base-url}") String baseUrl,
            @Value("${app.mercadopago.access-token}") String accessToken) {
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
        this.restClient = RestClient.create();
        this.json = new JsonMapper();
    }

    @Override
    public JsonNode createPix(
            Long paymentId, BigDecimal amount, String email, String cpf, String idempotencyKey) {
        ObjectNode body = json.createObjectNode();
        body.put("transaction_amount", amount.doubleValue());
        body.put("description", "Pagamento do pedido #" + paymentId);
        body.put("payment_method_id", "pix");
        body.put("external_reference", paymentId.toString());
        body.put("date_of_expiration", Instant.now().plus(30, ChronoUnit.MINUTES).toString());

        ObjectNode payer = body.putObject("payer");
        payer.put("email", email);
        ObjectNode identification = payer.putObject("identification");
        identification.put("type", "CPF");
        identification.put("number", cpf);

        body.put("notification_url", "http://localhost:8084/webhooks/mercadopago");

        return post("/v1/payments", body.toString(), idempotencyKey);
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
        ObjectNode body = json.createObjectNode();
        body.put("transaction_amount", amount.doubleValue());
        body.put("description", "Pagamento do pedido #" + paymentId);
        body.put("token", token);
        body.put("payment_method_id", paymentMethodId);
        body.put("issuer_id", issuerId);
        body.put("installments", installments);
        body.put("external_reference", paymentId.toString());

        ObjectNode payer = body.putObject("payer");
        payer.put("email", email);
        ObjectNode identification = payer.putObject("identification");
        identification.put("type", "CPF");
        identification.put("number", cpf);

        return post("/v1/payments", body.toString(), idempotencyKey);
    }

    @Override
    public JsonNode createPreference(
            Long paymentId, BigDecimal amount, String itemDescription, String idempotencyKey) {
        ObjectNode body = json.createObjectNode();
        body.put("external_reference", paymentId.toString());

        var items = body.putArray("items");
        ObjectNode item = items.addObject();
        item.put("title", itemDescription);
        item.put("quantity", 1);
        item.put("unit_price", amount.doubleValue());
        item.put("currency_id", "BRL");

        ObjectNode backUrls = body.putObject("back_urls");
        backUrls.put("success", "http://localhost:3000/orders/" + paymentId);
        backUrls.put("pending", "http://localhost:3000/orders/" + paymentId);
        backUrls.put("failure", "http://localhost:3000/orders/" + paymentId);

        body.put("notification_url", "http://localhost:8084/webhooks/mercadopago");
        body.put("expires", true);
        body.put("expiration_date_to", Instant.now().plus(24, ChronoUnit.HOURS).toString());

        return post("/checkout/preferences", body.toString(), idempotencyKey);
    }

    @Override
    public JsonNode getPayment(String externalId) {
        return get("/v1/payments/" + externalId);
    }

    @Override
    public JsonNode refund(String externalId, BigDecimal amount, String idempotencyKey) {
        String body = amount != null ? "{\"amount\":" + amount + "}" : "{}";
        return post("/v1/payments/" + externalId + "/refunds", body, idempotencyKey);
    }

    @Override
    public JsonNode cancel(String externalId) {
        String body = "{\"status\": \"cancelled\"}";
        return put("/v1/payments/" + externalId, body);
    }

    @Override
    public JsonNode getPaymentMethods() {
        return get("/v1/payment_methods");
    }

    private JsonNode get(String path) {
        try {
            return restClient.get()
                    .uri(baseUrl + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Erro ao fazer GET {}", path, e);
            throw new RuntimeException("Erro ao conectar ao Mercado Pago", e);
        }
    }

    private JsonNode post(String path, String body, String idempotencyKey) {
        try {
            return restClient.post()
                    .uri(baseUrl + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Erro ao fazer POST {}", path, e);
            throw new RuntimeException("Erro ao conectar ao Mercado Pago", e);
        }
    }

    private JsonNode put(String path, String body) {
        try {
            return restClient.put()
                    .uri(baseUrl + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Erro ao fazer PUT {}", path, e);
            throw new RuntimeException("Erro ao conectar ao Mercado Pago", e);
        }
    }
}
