package ecommerce_event_driven.inventory.modules.product.application.dtos;

public record PatchStockRequest(
        Integer stock,
        Integer delta) {
}
