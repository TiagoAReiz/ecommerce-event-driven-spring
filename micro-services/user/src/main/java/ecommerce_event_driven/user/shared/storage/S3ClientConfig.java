package ecommerce_event_driven.user.shared.storage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Um cliente so: quem baixa a foto do Google e envia ao bucket e o proprio
 * servico (S3AvatarStorageAdapter), nunca o navegador - por isso nao existe
 * presigner aqui, diferente do inventory (que assina PUT para o navegador
 * enviar a foto de produto direto). O client fala com o MinIO pela rede do
 * compose (app.s3.endpoint = http://minio:9000).
 */
@Configuration
public class S3ClientConfig {

    // MinIO nao usa regiao de verdade, mas o SDK exige uma para montar a assinatura.
    private static final Region REGION = Region.US_EAST_1;

    @Bean
    public S3Client internalS3Client(
            @Value("${app.s3.endpoint}") String endpoint,
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey) {

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true) // MinIO nao resolve bucket por subdominio
                .build();
    }
}
