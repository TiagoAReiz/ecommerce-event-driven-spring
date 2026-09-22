package ecommerce_event_driven.inventory.modules.product.application.ports.outbound.storage;

/**
 * Armazenamento de objeto (S3 / MinIO) para as fotos de produto. Gera a url
 * assinada de upload e apaga o objeto quando a foto e removida.
 */
public interface PhotoStoragePort {

    /**
     * Gera uma url assinada de PUT valida por alguns minutos. A chave do
     * objeto e sempre gerada aqui - nunca o nome que o cliente mandou, que
     * pode colidir ou carregar caminho.
     */
    UploadUrl createUploadUrl(Long idProduct, String extension);

    /**
     * Gera uma url assinada de PUT para a tela de criacao de produto, antes de
     * existir id. A chave fica sob rascunho/ - o compose configura expiracao
     * de 1 dia para o que nao for promovido.
     */
    UploadUrl createDraftUploadUrl(String extension);

    /**
     * Promove um objeto de rascunho/ para a pasta do produto (copia + apaga o
     * rascunho) e devolve a url final. Se a url nao for do nosso bucket ou nao
     * estiver sob rascunho/, devolve a mesma url intacta (link externo colado
     * pelo dono). Falha na movimentacao nao propaga: quem chama grava a url de
     * rascunho mesmo assim (ver S3PhotoStorageAdapter - aqui a falha ja vem
     * tratada e logada).
     */
    String promoteDraft(String publicUrl, Long idProduct);

    /**
     * Apaga o objeto se a url apontar para o nosso bucket; senao, nao faz nada.
     * Falha ao apagar nao propaga: quem chama decide o que fazer (ver
     * S3PhotoStorageAdapter - aqui a falha ja vem tratada e logada).
     */
    void deleteIfOwned(String publicUrl);

    /** Prefixo publico do nosso bucket, para saber o que e foto nossa. */
    String publicPrefix();

    record UploadUrl(String uploadUrl, String publicUrl, long expiresIn) {
    }
}
