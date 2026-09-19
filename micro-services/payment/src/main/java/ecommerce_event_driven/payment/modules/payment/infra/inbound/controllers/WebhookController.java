package ecommerce_event_driven.payment.modules.payment.infra.inbound.controllers;

import ecommerce_event_driven.payment.modules.payment.application.usecases.ProcessWebhookUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {
    private final ProcessWebhookUseCase processWebhookUseCase;

    public WebhookController(ProcessWebhookUseCase processWebhookUseCase) {
        this.processWebhookUseCase = processWebhookUseCase;
    }

    @PostMapping("/mercadopago")
    public ResponseEntity<?> handleMercadoPagoWebhook(@RequestBody JsonNode payload) {
        processWebhookUseCase.execute(payload);
        return ResponseEntity.ok().build();
    }
}
