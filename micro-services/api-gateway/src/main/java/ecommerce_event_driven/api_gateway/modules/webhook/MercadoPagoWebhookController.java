package ecommerce_event_driven.api_gateway.modules.webhook;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.IssuedToken;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security.TokenIssuerPort;
import ecommerce_event_driven.api_gateway.shared.web.BadRequestException;
import ecommerce_event_driven.api_gateway.shared.web.UnprocessableException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recebe webhooks do Mercado Pago, valida assinatura HMAC e repassa ao payment.
 *
 * POST /public/webhooks/mercadopago
 * Header: x-signature: ts=<ts>,v1=<hex>
 * Query/Body: data.id, type
 */
@RestController
@RequestMapping("/public/webhooks/mercadopago")
public class MercadoPagoWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoSignature signature;
    private final TokenIssuerPort tokenIssuer;
    private final JsonMapper jsonMapper;
    private final RestClient paymentClient;

    public MercadoPagoWebhookController(
            @Value("${app.mercadopago.webhook-secret}") String webhookSecret,
            TokenIssuerPort tokenIssuer,
            JsonMapper jsonMapper,
            @Value("${app.services.payment.url}") String paymentServiceUrl) {
        this.signature = new MercadoPagoSignature(webhookSecret);
        this.tokenIssuer = tokenIssuer;
        this.jsonMapper = jsonMapper;
        this.paymentClient = RestClient.create(paymentServiceUrl);
    }

    @PostMapping
    public ResponseEntity<?> handleWebhook(HttpServletRequest request) throws IOException {
        String requestId = request.getHeader("X-Request-Id");

        // Lê a assinatura
        String xSignature = request.getHeader("x-signature");
        if (xSignature == null || xSignature.isEmpty()) {
            logger.warn("Missing x-signature header");
            return ResponseEntity.status(401).build();
        }

        String timestamp = extractTimestamp(xSignature);
        String v1 = extractV1(xSignature);
        if (timestamp == null || v1 == null) {
            logger.warn("Invalid x-signature format");
            return ResponseEntity.status(401).build();
        }

        // Lê o corpo
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        Map<String, Object> body = jsonMapper.readValue(bodyBytes, Map.class);

        // Extrai data.id (da query ou do corpo)
        String dataId = request.getParameter("data.id");
        if (dataId == null || dataId.isEmpty()) {
            Object data = body.get("data");
            if (data instanceof Map) {
                dataId = (String) ((Map<?, ?>) data).get("id");
            }
        }
        if (dataId == null || dataId.isEmpty()) {
            logger.warn("Missing data.id");
            return ResponseEntity.status(400).build();
        }

        // Extrai type
        String type = request.getParameter("type");
        if (type == null || type.isEmpty()) {
            type = (String) body.get("type");
        }
        if (type == null || type.isEmpty()) {
            logger.warn("Missing type");
            return ResponseEntity.status(400).build();
        }

        // Valida type reconhecido
        if (!"payment".equals(type)) {
            logger.warn("Unrecognized webhook type: {}", type);
            return ResponseEntity.status(422).build();
        }

        // Valida assinatura
        try {
            if (!signature.validate(dataId, requestId != null ? requestId : "", timestamp, v1)) {
                logger.warn("Invalid webhook signature");
                return ResponseEntity.status(401).build();
            }
        } catch (IllegalArgumentException e) {
            // Timestamp fora da janela de 5 minutos
            logger.warn("Webhook timestamp out of range: {}", e.getMessage());
            return ResponseEntity.status(408).build();
        }

        // Repassa ao payment
        return repassToPayment(body, requestId);
    }

    private ResponseEntity<?> repassToPayment(Map<String, Object> body, String requestId) {
        try {
            // Emite token interno com escopos webhooks:ingest e internal:hydrate
            Set<String> scopes = Set.of("webhooks:ingest", "internal:hydrate");
            IssuedToken token = tokenIssuer.issueInternal("svc:api-gateway", List.of(), scopes);

            // Prepara envelope
            Map<String, Object> envelope = Map.of(
                    "signatureVerified", true,
                    "receivedAt", Instant.now(),
                    "payload", body
            );

            String envelopeJson = jsonMapper.writeValueAsString(envelope);

            // Repassa ao payment
            var response = paymentClient.post()
                    .uri("/webhooks/mercadopago")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                    .header("X-Request-Id", requestId != null ? requestId : "")
                    .body(envelopeJson)
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();

                        // 2xx ou erro de negocio (404/409/422) -> responde 200
                        if (status >= 200 && status < 300) {
                            return ResponseEntity.ok().build();
                        }
                        if (status == 404 || status == 409 || status == 422) {
                            logger.info("Payment service returned {}, responding 200 to MP", status);
                            return ResponseEntity.ok().build();
                        }

                        // 5xx ou timeout -> repassa erro para MP reenviar
                        if (status >= 500) {
                            return ResponseEntity.status(status).build();
                        }

                        // Outros erros
                        return ResponseEntity.ok().build();
                    });

            return response;

        } catch (Exception e) {
            logger.error("Failed to repass webhook to payment", e);
            return ResponseEntity.status(503).build();
        }
    }

    private String extractTimestamp(String xSignature) {
        // x-signature: ts=123456789,v1=abc...
        String[] parts = xSignature.split(",");
        for (String part : parts) {
            if (part.startsWith("ts=")) {
                return part.substring(3);
            }
        }
        return null;
    }

    private String extractV1(String xSignature) {
        String[] parts = xSignature.split(",");
        for (String part : parts) {
            if (part.startsWith("v1=")) {
                return part.substring(3);
            }
        }
        return null;
    }
}
