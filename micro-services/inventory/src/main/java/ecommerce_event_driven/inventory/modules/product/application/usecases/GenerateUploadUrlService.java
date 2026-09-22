package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlResponse;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.GenerateUploadUrlUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.ProductRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.storage.PhotoStoragePort;
import ecommerce_event_driven.inventory.shared.web.NotFoundException;
import org.springframework.stereotype.Service;

@Service
public class GenerateUploadUrlService implements GenerateUploadUrlUseCase {

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

        String extension = PhotoUploadPolicy.extensionFor(request.contentType());
        PhotoUploadPolicy.validateSize(request.sizeBytes());

        var uploadUrl = photoStorage.createUploadUrl(idProduct, extension);
        return new UploadUrlResponse(uploadUrl.uploadUrl(), uploadUrl.publicUrl(), uploadUrl.expiresIn());
    }
}
