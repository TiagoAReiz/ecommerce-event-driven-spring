package ecommerce_event_driven.inventory.modules.product.application.dtos;

import ecommerce_event_driven.inventory.shared.web.PageMeta;
import java.util.List;

public record ProductSearchResponse(
        List<ProductSearchItem> content,
        PageMeta page,
        FacetsResponse facets) {

    public record ProductSearchItem(
            Long id,
            String name,
            String price,
            String rating,
            Integer ratingCount,
            Integer available,
            String photoUrl,
            CategoryResponse category) {
    }

    public record FacetsResponse(
            List<CategoryFacet> categories,
            PriceRangeFacet priceRange) {

        public record CategoryFacet(
                Long id,
                String name,
                long count) {
        }

        public record PriceRangeFacet(
                String min,
                String max) {
        }
    }
}
