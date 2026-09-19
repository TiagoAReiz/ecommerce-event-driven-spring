package ecommerce_event_driven.api_gateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Carrega o par RSA usado para assinar os tokens.
 *
 * <p>O mesmo bean RSAKey alimenta o emissor e o endpoint JWKS, entao o kid
 * publicado e sempre o mesmo que vai no cabecalho do token. O kid vem do
 * thumbprint da chave: e deterministico, logo sobrevive a restart sem
 * invalidar token nenhum.
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAKey jwtSigningKey(
            @Value("${app.jwt.private-key}") Resource privateKeyPem,
            @Value("${app.jwt.public-key}") Resource publicKeyPem) throws Exception {

        RSAPrivateKey privateKey = read(privateKeyPem, RsaKeyConverters.pkcs8()::convert);
        RSAPublicKey publicKey = read(publicKeyPem, RsaKeyConverters.x509()::convert);

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint()
                .build();
    }

    /**
     * Recebe o JWKSource em vez de NimbusJwtEncoder.withKeyPair(...) de proposito:
     * assim o encoder assina com exatamente a mesma RSAKey que o JWKS publica,
     * incluindo o kid. Com withKeyPair o encoder geraria um kid proprio.
     */
    @Bean
    public JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    private static <T> T read(Resource pem, KeyReader<T> reader) throws IOException {
        try (InputStream in = pem.getInputStream()) {
            return reader.read(in);
        }
    }

    @FunctionalInterface
    private interface KeyReader<T> {
        T read(InputStream in);
    }
}
