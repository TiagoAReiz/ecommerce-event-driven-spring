package ecommerce_event_driven.inventory.modules.product.application.dtos;

/** Resposta de POST /products/{id}/photos/upload-url. */
public record UploadUrlResponse(
        String uploadUrl,
        String publicUrl,
        long expiresIn) {
}
