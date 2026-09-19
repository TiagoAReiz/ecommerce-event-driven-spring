package ecommerce_event_driven.inventory.shared.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta paginada com metadados de paginacao.
 */
public record PageResponse<T>(
        @JsonProperty("content")
        List<T> content,

        @JsonProperty("page")
        PageMeta page) {
}
