package ecommerce_event_driven.shipment.modules.shipment.application.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/**
 * Resposta de calculo de frete.
 */
public record ShippingQuoteResponse(
        String zipcode,
        Origin origin,
        Integer distanceKm,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal ratePerKm,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal freightCost) {

    public record Origin(String city, String state) {}
}
