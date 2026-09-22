package ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases;

import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlResponse;

/**
 * Gera uma url assinada de PUT para o navegador subir a foto ANTES do
 * produto existir (tela de criacao): o id so nasce no INSERT, entao nao ha
 * como usar a rota com {id}. O objeto fica em rascunho/ e e promovido para
 * a pasta do produto em POST /products, quando o id passa a existir.
 */
public interface GenerateDraftUploadUrlUseCase {

    UploadUrlResponse execute(UploadUrlRequest request);
}
