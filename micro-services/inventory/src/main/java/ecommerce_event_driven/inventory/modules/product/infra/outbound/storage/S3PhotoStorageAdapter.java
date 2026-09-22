package ecommerce_event_driven.inventory.modules.product.infra.outbound.storage;

import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.storage.PhotoStoragePort;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3PhotoStorageAdapter implements PhotoStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3PhotoStorageAdapter.class);

    // 5 minutos: o mesmo prazo documentado no contrato da rota de upload-url.
    private static final Duration EXPIRES_IN = Duration.ofMinutes(5);

    private final S3Client internalS3Client;
    private final S3Presigner publicS3Presigner;
    private final String bucket;
    private final String publicUrlBase;

    public S3PhotoStorageAdapter(
            S3Client internalS3Client,
            S3Presigner publicS3Presigner,
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.public-url}") String publicUrlBase) {
        this.internalS3Client = internalS3Client;
        this.publicS3Presigner = publicS3Presigner;
        this.bucket = bucket;
        this.publicUrlBase = publicUrlBase;
    }

    @Override
    public UploadUrl createUploadUrl(Long idProduct, String extension) {
        // Chave gerada pelo servidor: nome de arquivo do cliente pode colidir
        // ou carregar caminho (path traversal), entao nunca vira chave direto.
        String key = idProduct + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        // Ponto critico: assina contra o presigner publico (S3_PUBLIC_URL), porque
        // quem usa esta url e o navegador - `minio:9000` nao resolve fora do compose.
        // Assinar e calculo local, entao funciona mesmo o servico falando com o
        // minio por outro endereco.
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(EXPIRES_IN)
                .putObjectRequest(putRequest)
                .build();

        String uploadUrl = publicS3Presigner.presignPutObject(presignRequest).url().toString();
        String publicUrl = publicUrlBase + "/" + bucket + "/" + key;

        return new UploadUrl(uploadUrl, publicUrl, EXPIRES_IN.toSeconds());
    }

    @Override
    public String publicPrefix() {
        return publicUrlBase + "/" + bucket + "/";
    }

    @Override
    public void deleteIfOwned(String publicUrl) {
        String prefix = publicPrefix();
        if (publicUrl == null || !publicUrl.startsWith(prefix)) {
            // Nao e um objeto do nosso bucket (ex.: url antiga de outro host) - nada a apagar.
            return;
        }

        String key = publicUrl.substring(prefix.length());
        try {
            internalS3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (Exception ex) {
            // O registro no banco e a verdade: objeto orfao no S3 nao pode
            // derrubar a remocao da foto. So loga e segue.
            log.warn("Falha ao apagar objeto do S3 para a foto {}: {}", publicUrl, ex.getMessage());
        }
    }
}
