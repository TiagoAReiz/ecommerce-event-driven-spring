package ecommerce_event_driven.inventory.modules.product.application.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

/**
 * DTO de categoria na vitrine: id, name, slug e productCount (produtos ativos).
 */
public record CategoryResponse(
        @JsonProperty("id")
        Long id,

        @JsonProperty("name")
        String name,

        @JsonProperty("slug")
        String slug,

        @JsonProperty("productCount")
        long productCount) implements Serializable {
}
