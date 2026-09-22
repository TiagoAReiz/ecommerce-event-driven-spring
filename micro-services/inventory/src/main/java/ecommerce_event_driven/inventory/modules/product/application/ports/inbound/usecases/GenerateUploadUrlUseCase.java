package ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases;

import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlResponse;

/**
 * Gera uma url assinada de PUT para o navegador subir a foto direto no S3
 * (MinIO), sem passar pelo backend. O front registra a foto depois, com a
 * publicUrl devolvida aqui, em POST /products/{id}/photos.
 */
public interface GenerateUploadUrlUseCase {

    UploadUrlResponse execute(Long idProduct, UploadUrlRequest request);
}
