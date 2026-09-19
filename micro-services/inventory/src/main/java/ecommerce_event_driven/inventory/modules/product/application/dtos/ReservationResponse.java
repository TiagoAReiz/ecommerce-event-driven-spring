package ecommerce_event_driven.inventory.modules.product.application.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record ReservationResponse(
        @JsonProperty("idOrder")
        Long idOrder,

        @JsonProperty("items")
        List<ReservationItem> items) {

    public record ReservationItem(
            @JsonProperty("idProduct")
            Long idProduct,

            @JsonProperty("quantity")
            Integer quantity,

            @JsonProperty("status")
            String status,

            @JsonProperty("expiresAt")
            Instant expiresAt) {
    }
}
