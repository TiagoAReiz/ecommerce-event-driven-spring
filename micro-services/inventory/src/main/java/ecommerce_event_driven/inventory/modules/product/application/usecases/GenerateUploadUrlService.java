package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlResponse;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.GenerateUploadUrlUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.ProductRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.storage.PhotoStoragePort;
import ecommerce_event_driven.inventory.shared.web.NotFoundException;
import ecommerce_event_driven.inventory.shared.web.UnprocessableException;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GenerateUploadUrlService implements GenerateUploadUrlUseCase {

    // 5 MB: mesmo teto documentado no contrato da rota.
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    // Extensao vem do contentType, nunca do fileName do cliente - fecha o
    // conjunto aceito e evita chave de objeto com extensao arbitraria.
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/avif", "avif");

    private final ProductRepositoryPort productRepository;
    private final PhotoStoragePort photoStorage;

    public GenerateUploadUrlService(ProductRepositoryPort productRepository, PhotoStoragePort photoStorage) {
        this.productRepository = productRepository;
        this.photoStorage = photoStorage;
    }

    @Override
    public UploadUrlResponse execute(Long idProduct, UploadUrlRequest request) {
        // findById ja enxerga so produto ativo (nao removido).
        productRepository.findById(idProduct)
                .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        String extension = ALLOWED_CONTENT_TYPES.get(request.contentType());
        if (extension == null) {
            throw new UnprocessableException(
                    "contentType deve ser image/jpeg, image/png, image/webp ou image/avif",
                    "INVALID_CONTENT_TYPE");
        }

        if (request.sizeBytes() > MAX_SIZE_BYTES) {
            throw new UnprocessableException(
                    "sizeBytes acima do limite de 5 MB",
                    "FILE_TOO_LARGE");
        }

        var uploadUrl = photoStorage.createUploadUrl(idProduct, extension);
        return new UploadUrlResponse(uploadUrl.uploadUrl(), uploadUrl.publicUrl(), uploadUrl.expiresIn());
    }
}
