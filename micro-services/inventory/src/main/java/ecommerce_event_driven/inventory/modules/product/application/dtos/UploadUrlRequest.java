package ecommerce_event_driven.inventory.modules.product.application.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Corpo de POST /products/{id}/photos/upload-url. */
public record UploadUrlRequest(
        @NotBlank(message = "fileName é obrigatório")
        String fileName,

        @NotBlank(message = "contentType é obrigatório")
        String contentType,

        @NotNull(message = "sizeBytes é obrigatório")
        @Positive(message = "sizeBytes deve ser positivo")
        Long sizeBytes) {
}
