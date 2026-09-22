package ecommerce_event_driven.inventory.shared.storage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Dois clientes porque os hosts sao diferentes por natureza: o backend fala com
 * o MinIO pela rede do compose (app.s3.endpoint = http://minio:9000), mas quem
 * usa a url assinada e o navegador, que so enxerga app.s3.public-url. Uma url
 * assinada vale so para o host com que foi assinada - client interno e
 * presigner publico nao podem compartilhar endpoint.
 */
@Configuration
public class S3ClientConfig {

    // MinIO nao usa regiao de verdade, mas o SDK exige uma para montar a assinatura.
    private static final Region REGION = Region.US_EAST_1;

    // Cliente para operacoes do proprio servico (ex.: apagar objeto ao remover foto).
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

    // Presigner para a url de upload que o navegador vai usar diretamente.
    @Bean
    public S3Presigner publicS3Presigner(
            @Value("${app.s3.public-url}") String publicUrl,
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey) {

        return S3Presigner.builder()
                .endpointOverride(URI.create(publicUrl))
                .region(REGION)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
