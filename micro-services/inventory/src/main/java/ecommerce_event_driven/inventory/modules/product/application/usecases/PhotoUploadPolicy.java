package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.shared.web.UnprocessableException;
import java.util.Map;

/**
 * Regras de tipo e tamanho de foto de produto, compartilhadas entre o upload
 * direto (produto ja existe) e o upload em rascunho (tela de criacao, sem id
 * ainda). Mesmo teto documentado no contrato das duas rotas de upload-url.
 */
final class PhotoUploadPolicy {

    static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    // Extensao vem do contentType, nunca do fileName do cliente - fecha o
    // conjunto aceito e evita chave de objeto com extensao arbitraria.
    static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/avif", "avif");

    private PhotoUploadPolicy() {
    }

    static String extensionFor(String contentType) {
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new UnprocessableException(
                    "contentType deve ser image/jpeg, image/png, image/webp ou image/avif",
                    "INVALID_CONTENT_TYPE");
        }
        return extension;
    }

    static void validateSize(long sizeBytes) {
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new UnprocessableException(
                    "sizeBytes acima do limite de 5 MB",
                    "FILE_TOO_LARGE");
        }
    }
}
