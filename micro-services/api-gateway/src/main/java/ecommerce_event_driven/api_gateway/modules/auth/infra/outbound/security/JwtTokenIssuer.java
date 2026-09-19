package ecommerce_event_driven.api_gateway.modules.auth.infra.outbound.security;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.IssuedToken;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.UserResponse;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security.TokenIssuerPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Assina os JWT em RS256 com a chave privada do gateway.
 *
 * <p>As duas audiencias abaixo sao contrato com os outros servicos: eles
 * configuram spring.security.oauth2.resourceserver.jwt.audiences=internal, o
 * que faz o Spring recusar qualquer token que nao tenha essa aud. E por isso
 * que o token do browser, mesmo assinado por esta mesma chave, nao abre porta
 * nenhuma na rede interna: ele sai com aud=front.
 */
@Component
public class JwtTokenIssuer implements TokenIssuerPort {

    /** Token do usuario final. Sai do gateway e vive no browser. */
    private static final String BROWSER_AUDIENCE = "front";

    /** Token que circula so entre gateway e microservicos. */
    private static final String INTERNAL_AUDIENCE = "internal";

    /**
     * O minimo para o login funcionar: achar o usuario e cria-lo no primeiro
     * acesso. Nao da acesso a pedido, pagamento ou estoque.
     */
    private static final String LOGIN_SCOPE = "users:read users:write";

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration userTtl;
    private final Duration serviceTtl;

    public JwtTokenIssuer(
            JwtEncoder encoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.ttl}") Duration userTtl,
            @Value("${app.jwt.service-ttl}") Duration serviceTtl) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.userTtl = userTtl;
        this.serviceTtl = serviceTtl;
    }

    /**
     * O subject e o id no microservico user, nao o sub do Google: para os
     * demais servicos o dono do pedido e o id de users. Email e nome vao como
     * claim para o front nao precisar de um round-trip so para exibir o nome.
     */
    @Override
    public IssuedToken issueForUser(UserResponse user) {
        return sign(JwtClaimsSet.builder()
                .audience(List.of(BROWSER_AUDIENCE))
                .subject(String.valueOf(user.id()))
                .claim("email", user.email())
                .claim("name", user.name()), userTtl);
    }

    /**
     * Sem subject de usuario: quem age aqui e o proprio gateway. Vida curta
     * porque e usado na mesma requisicao em que foi criado.
     */
    @Override
    public IssuedToken issueForLogin() {
        return sign(JwtClaimsSet.builder()
                .audience(List.of(INTERNAL_AUDIENCE))
                .subject("api-gateway")
                .claim("scope", LOGIN_SCOPE), serviceTtl);
    }

    private IssuedToken sign(JwtClaimsSet.Builder claims, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        JwtClaimsSet claimsSet = claims
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        String value = encoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }
}
