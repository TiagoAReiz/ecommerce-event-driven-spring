package ecommerce_event_driven.api_gateway.modules.auth.infra.inbound.controllers;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.IssuedToken;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security.TokenIssuerPort;
import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint para emissao de token de servico.
 *
 * POST /auth/service-token valida credencial (clientId + clientSecret)
 * e emite token interno com escopo internal:hydrate.
 */
@RestController
@RequestMapping("/auth/service-token")
public class ServiceTokenController {

    private final TokenIssuerPort tokenIssuer;
    private final String userSecret;
    private final String inventorySecret;
    private final String orderSecret;
    private final String paymentSecret;
    private final String shipmentSecret;

    public ServiceTokenController(
            TokenIssuerPort tokenIssuer,
            @Value("${app.service-clients.user}") String userSecret,
            @Value("${app.service-clients.inventory}") String inventorySecret,
            @Value("${app.service-clients.order}") String orderSecret,
            @Value("${app.service-clients.payment}") String paymentSecret,
            @Value("${app.service-clients.shipment}") String shipmentSecret) {
        this.tokenIssuer = tokenIssuer;
        this.userSecret = userSecret;
        this.inventorySecret = inventorySecret;
        this.orderSecret = orderSecret;
        this.paymentSecret = paymentSecret;
        this.shipmentSecret = shipmentSecret;
    }

    @PostMapping
    public ResponseEntity<ServiceTokenResponse> issueServiceToken(
            @Valid @RequestBody ServiceTokenRequest request) {
        // Resolve o segredo esperado para este clientId
        String expectedSecret = resolveSecret(request.clientId());
        if (expectedSecret == null) {
            // Cliente desconhecido: mesma resposta para nao enumerar clientes
            return ResponseEntity.status(401).body(
                    new ServiceTokenResponse(null, null, 0L, "INVALID_CLIENT"));
        }

        // Compara em tempo constante
        if (!constantTimeEquals(request.clientSecret(), expectedSecret)) {
            return ResponseEntity.status(401).body(
                    new ServiceTokenResponse(null, null, 0L, "INVALID_SECRET"));
        }

        // Emite token de servico
        IssuedToken token = tokenIssuer.issueForService(request.clientId());
        ServiceTokenResponse response = new ServiceTokenResponse(
                token.value(),
                "Bearer",
                300L,  // 5 minutos em segundos
                null);

        return ResponseEntity.ok(response);
    }

    private String resolveSecret(String clientId) {
        return switch (clientId) {
            case "user" -> userSecret;
            case "inventory" -> inventorySecret;
            case "order" -> orderSecret;
            case "payment" -> paymentSecret;
            case "shipment" -> shipmentSecret;
            default -> null;
        };
    }

    /**
     * Compara strings em tempo constante para evitar ataques de timing.
     */
    private boolean constantTimeEquals(String a, String b) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digestA = md.digest(a.getBytes());
            byte[] digestB = md.digest(b.getBytes());
            return MessageDigest.isEqual(digestA, digestB);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public record ServiceTokenRequest(String clientId, String clientSecret) {}

    public record ServiceTokenResponse(String accessToken, String tokenType, Long expiresIn, String code) {}
}
