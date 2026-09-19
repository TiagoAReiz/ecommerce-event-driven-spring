package ecommerce_event_driven.inventory.modules.product.application.dtos;

public record UpdateProductRequest(
        String name,
        String description,
        Long idCategory,
        String price) {
}
