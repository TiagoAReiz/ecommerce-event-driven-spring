package ecommerce_event_driven.user.modules.user.infra.outbound.storage;

import ecommerce_event_driven.user.modules.user.application.ports.outbound.storage.AvatarStoragePort;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3AvatarStorageAdapter implements AvatarStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3AvatarStorageAdapter.class);

    // Pasta de avatar, separada de rascunho/ e das pastas de produto do inventory
    // no mesmo bucket.
    private static final String AVATAR_PREFIX = "avatares/";

    // Tempo curto: isto roda no caminho de login, ninguem pode ficar preso
    // esperando o CDN do Google responder.
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // Foto de perfil nao precisa de mais que isso; teto evita que um host
    // hostil (ou o proprio Google um dia) prenda o servico baixando um arquivo gigante.
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    // So os formatos que o front sabe exibir como <img>. Decisao pelo
    // Content-Type da resposta, nunca pela extensao da url (que pode nem existir,
    // caso do Google: .../photo.jpg=s96-c).
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final S3Client internalS3Client;
    private final HttpClient httpClient;
    private final String bucket;
    private final String publicUrlBase;

    public S3AvatarStorageAdapter(
            S3Client internalS3Client,
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.public-url}") String publicUrlBase) {
        this.internalS3Client = internalS3Client;
        this.bucket = bucket;
        this.publicUrlBase = publicUrlBase;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public String guardar(Long idUsuario, String urlExterna) {
        if (urlExterna == null || urlExterna.isBlank()) {
            // Sem foto no Google - nada a copiar.
            return null;
        }

        if (urlExterna.startsWith(publicPrefix())) {
            // Ja e nossa (ex.: usuario recriado a partir de um snapshot ja
            // migrado) - baixar de novo so gastaria banda a toa.
            return urlExterna;
        }

        try {
            byte[] photo = download(urlExterna, idUsuario);
            if (photo == null) {
                return null;
            }

            String contentType = lastDownloadContentType;
            String extension = ALLOWED_CONTENT_TYPES.get(contentType);
            String key = AVATAR_PREFIX + idUsuario + "." + extension;

            // Chave deterministica por usuario: uma chamada seguinte com foto
            // diferente sobrescreve o mesmo objeto, sem deixar lixo no bucket.
            internalS3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(photo));

            return publicUrlBase + "/" + bucket + "/" + key;
        } catch (Exception ex) {
            // Download, tipo, tamanho ou envio ao bucket podem falhar de varias
            // formas - nenhuma pode derrubar o login. So loga e segue sem foto.
            log.warn("Falha ao copiar foto de perfil de {} para o usuario {}: {}",
                    urlExterna, idUsuario, ex.getMessage());
            return null;
        }
    }

    // Guarda o Content-Type da ultima resposta lida em download(), para nao
    // precisar de um record/tupla so para isso - download() ja loga e devolve
    // null em todo caso de erro, entao o campo so importa junto de um retorno
    // nao nulo.
    private String lastDownloadContentType;

    private byte[] download(String urlExterna, Long idUsuario) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(urlExterna))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            log.warn("Google respondeu {} ao baixar a foto de perfil do usuario {}",
                    response.statusCode(), idUsuario);
            drain(response.body());
            return null;
        }

        String contentType = response.headers().firstValue("Content-Type")
                .map(value -> value.split(";")[0].trim().toLowerCase())
                .orElse("");
        if (!ALLOWED_CONTENT_TYPES.containsKey(contentType)) {
            log.warn("Tipo de imagem nao aceito ({}) na foto de perfil do usuario {}",
                    contentType, idUsuario);
            drain(response.body());
            return null;
        }

        byte[] body = readAtMost(response.body(), MAX_BYTES);
        if (body.length == 0) {
            log.warn("Foto de perfil do usuario {} veio vazia", idUsuario);
            return null;
        }

        this.lastDownloadContentType = contentType;
        return body;
    }

    /** Le no maximo `maxBytes` do stream; estoura se o corpo for maior. */
    private static byte[] readAtMost(InputStream in, long maxBytes) throws IOException {
        try (in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("imagem maior que o limite de " + maxBytes + " bytes");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static void drain(InputStream in) {
        try (in) {
            in.readAllBytes();
        } catch (IOException ignored) {
            // Corpo de uma resposta que ja vai ser descartada - falha aqui e irrelevante.
        }
    }

    @Override
    public String publicPrefix() {
        return publicUrlBase + "/" + bucket + "/";
    }
}
