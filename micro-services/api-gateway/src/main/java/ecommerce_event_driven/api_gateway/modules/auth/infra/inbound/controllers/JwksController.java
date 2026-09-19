package ecommerce_event_driven.api_gateway.modules.auth.infra.inbound.controllers;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publica a chave PUBLICA para os demais microservicos validarem os tokens.
 *
 * <p>Eles apontam spring.security.oauth2.resourceserver.jwt.jwk-set-uri para
 * esta URL, buscam a chave sozinhos e cacheiam. Nenhuma chave e copiada a mao.
 *
 * <p>toPublicJWKSet remove a parte privada: o que sai daqui e publico por design.
 */
@RestController
public class JwksController {

    private final Map<String, Object> publicJwkSet;

    public JwksController(RSAKey jwtSigningKey) {
        this.publicJwkSet = new JWKSet(jwtSigningKey).toPublicJWKSet().toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return publicJwkSet;
    }
}
