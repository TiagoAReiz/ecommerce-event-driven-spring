package ecommerce_event_driven.inventory.shared.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PageMeta(
        @JsonProperty("number")
        int number,

        @JsonProperty("size")
        int size,

        @JsonProperty("totalElements")
        long totalElements,

        @JsonProperty("totalPages")
        int totalPages) {
}
