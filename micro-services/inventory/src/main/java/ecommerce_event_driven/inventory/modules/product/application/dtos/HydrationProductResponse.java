package ecommerce_event_driven.inventory.modules.product.application.dtos;

import java.util.List;

public record HydrationProductResponse(
        List<HydrationProduct> products,
        List<Long> missing) {

    public record HydrationProduct(
            Long id,
            String name,
            String price,
            String photoUrl,
            Integer available,
            Boolean active) {
    }
}
