package ecommerce_event_driven.shipment.modules.shipment.infra.inbound.controllers;

import ecommerce_event_driven.shipment.modules.shipment.application.dtos.ShippingQuoteResponse;
import ecommerce_event_driven.shipment.modules.shipment.application.usecases.CalculateShippingQuoteService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller para calculo de frete.
 */
@RestController
@RequestMapping("/shipping")
public class ShippingQuoteController {
    private final CalculateShippingQuoteService quoteService;

    public ShippingQuoteController(CalculateShippingQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/quote")
    @PreAuthorize("hasAnyAuthority('SCOPE_shipments:read', 'SCOPE_internal:hydrate')")
    public ResponseEntity<ShippingQuoteResponse> quote(@RequestParam String zipcode) {
        ShippingQuoteResponse response = quoteService.execute(zipcode);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(response);
    }
}
