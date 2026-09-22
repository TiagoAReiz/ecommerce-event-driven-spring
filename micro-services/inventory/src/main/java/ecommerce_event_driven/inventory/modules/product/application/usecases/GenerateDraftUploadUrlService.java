package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlResponse;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.GenerateDraftUploadUrlUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.storage.PhotoStoragePort;
import org.springframework.stereotype.Service;

@Service
public class GenerateDraftUploadUrlService implements GenerateDraftUploadUrlUseCase {

    private final PhotoStoragePort photoStorage;

    public GenerateDraftUploadUrlService(PhotoStoragePort photoStorage) {
        this.photoStorage = photoStorage;
    }

    @Override
    public UploadUrlResponse execute(UploadUrlRequest request) {
        // Mesma validacao de tipo e tamanho da rota com id; sem verificacao de
        // produto, que aqui ainda nao existe.
        String extension = PhotoUploadPolicy.extensionFor(request.contentType());
        PhotoUploadPolicy.validateSize(request.sizeBytes());

        var uploadUrl = photoStorage.createDraftUploadUrl(extension);
        return new UploadUrlResponse(uploadUrl.uploadUrl(), uploadUrl.publicUrl(), uploadUrl.expiresIn());
    }
}
