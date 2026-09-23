package ecommerce_event_driven.user.modules.user.application.ports.outbound.storage;

/**
 * Copia a foto de perfil do Google para o nosso bucket (S3 / MinIO).
 *
 * <p>Existe porque o CDN do Google (`lh3.googleusercontent.com`) recusa a
 * imagem quando pedida a partir do nosso dominio (`net::ERR_BLOCKED_BY_ORB`) e
 * a url que ele manda no login rotaciona e um dia deixa de existir. Guardando
 * a nossa copia, `users.photo_url` passa a apontar sempre para algo que o
 * navegador consegue exibir.
 */
public interface AvatarStoragePort {

    /**
     * Baixa `urlExterna` e grava em `avatares/{idUsuario}.{extensao}` no
     * nosso bucket, devolvendo a url publica NOSSA.
     *
     * <p>A chave do objeto e sempre a mesma para o mesmo usuario, entao uma
     * chamada seguinte com uma foto do Google diferente sobrescreve o objeto
     * anterior - nao sobra lixo por usuario.
     *
     * <p>Se `urlExterna` ja apontar para o nosso bucket (comeca com
     * {@link #publicPrefix()}), devolve ela mesma sem baixar de novo.
     *
     * <p>Falha (timeout, tipo de imagem nao aceito, imagem grande demais, erro
     * ao gravar no bucket) nunca propaga: devolve {@code null} e o motivo fica
     * em log (ver S3AvatarStorageAdapter). A conta vale mais que o avatar - a
     * interface mostra a inicial do nome quando nao ha foto.
     */
    String guardar(Long idUsuario, String urlExterna);

    /** Prefixo publico do nosso bucket, para saber se uma url ja e nossa. */
    String publicPrefix();
}
