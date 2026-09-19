package ecommerce_event_driven.inventory.modules.product.application.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AvailabilityResponse(
        @JsonProperty("idProduct")
        Long idProduct,

        @JsonProperty("stock")
        Integer stock,

        @JsonProperty("held")
        Integer held,

        @JsonProperty("available")
        Integer available,

        @JsonProperty("asOf")
        Instant asOf) {
}
