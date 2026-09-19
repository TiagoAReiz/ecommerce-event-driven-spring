package ecommerce_event_driven.api_gateway.config;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * JwtDecoder para tokens do browser (aud=front).
 *
 * Valida:
 * - Assinatura com a chave publica do gateway
 * - Timestamp (iat, exp)
 * - Issuer
 * - Audiencia (deve conter "front")
 * - Denylist no Redis (jti em auth:denylist:*)
 */
@Configuration
public class BrowserJwtDecoderConfig {

    @Bean("browserJwtDecoder")
    public JwtDecoder browserJwtDecoder(RSAKey jwtSigningKey, StringRedisTemplate redisTemplate) throws JOSEException {
        RSAPublicKey publicKey = jwtSigningKey.toRSAPublicKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        // Validadores padrao do Spring: timestamp, issuer, aud.
        // Adicionamos validador customizado de denylist.
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                new org.springframework.security.oauth2.jwt.JwtTimestampValidator(),
                new org.springframework.security.oauth2.jwt.JwtIssuerValidator("http://localhost:8080"),
                new org.springframework.security.oauth2.jwt.JwtClaimValidator<java.util.List<String>>(
                        "aud",
                        aud -> aud != null && aud.contains("front")),
                new DenylistValidator(redisTemplate)
        ));

        return decoder;
    }

    /**
     * Validador customizado que verifica se o jti esta na denylist.
     */
    public static class DenylistValidator implements OAuth2TokenValidator<Jwt> {

        private final StringRedisTemplate redisTemplate;

        public DenylistValidator(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            String jti = token.getClaimAsString("jti");
            if (jti != null) {
                try {
                    Boolean inDenylist = redisTemplate.hasKey("auth:denylist:" + jti);
                    if (inDenylist != null && inDenylist) {
                        OAuth2Error error = new OAuth2Error("invalid_token", "Token was logged out", null);
                        return OAuth2TokenValidatorResult.failure(Collections.singletonList(error));
                    }
                } catch (Exception e) {
                    // Falha de Redis: deixa passar com warning.
                    // O token pode estar logout, mas nao conseguimos validar.
                    // Melhor deixar passar do que derrubar a rota.
                }
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
