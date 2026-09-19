package ecommerce_event_driven.api_gateway.modules.auth.infra.inbound.controllers;

import ecommerce_event_driven.api_gateway.modules.auth.application.ScopePolicy;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.UserResponse;
import ecommerce_event_driven.api_gateway.modules.auth.infra.outbound.cache.ProfileCache;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.external.UserMicroservicePort;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security.TokenIssuerPort;
import ecommerce_event_driven.api_gateway.shared.web.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Endpoints de sessao: perfil atual, renovacao e logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);
    private static final Duration SESSION_MAX_AGE = Duration.ofDays(7);

    private final ProfileCache profileCache;
    private final UserMicroservicePort userMicroservice;
    private final TokenIssuerPort tokenIssuer;
    private final ScopePolicy scopePolicy;
    private final StringRedisTemplate redisTemplate;

    public SessionController(
            ProfileCache profileCache,
            UserMicroservicePort userMicroservice,
            TokenIssuerPort tokenIssuer,
            ScopePolicy scopePolicy,
            StringRedisTemplate redisTemplate) {
        this.profileCache = profileCache;
        this.userMicroservice = userMicroservice;
        this.tokenIssuer = tokenIssuer;
        this.scopePolicy = scopePolicy;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> getSession(@AuthenticationPrincipal Jwt token) {
        Long userId = Long.parseLong(token.getSubject());
        Optional<UserResponse> cached = profileCache.get(userId);
        UserResponse user;
        if (cached.isPresent()) {
            user = cached.get();
        } else {
            user = userMicroservice.findProfile(userId)
                    .orElseThrow(() -> new NotFoundException("User not found", "USER_NOT_FOUND"));
            profileCache.set(user);
        }

        Instant expiresAt = Instant.ofEpochSecond(token.getExpiresAt().getEpochSecond());
        SessionResponse response = new SessionResponse(
                user.id(),
                user.name(),
                user.email(),
                user.photoUrl(),
                user.roles(),
                expiresAt);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@AuthenticationPrincipal Jwt token) {
        Long userId = Long.parseLong(token.getSubject());
        Instant authTime = Instant.ofEpochSecond(((Number) token.getClaim("auth_time")).longValue());

        // Verifica se a sessao expirou (> 7 dias)
        if (Instant.now().minus(SESSION_MAX_AGE).isAfter(authTime)) {
            return ResponseEntity.status(401).body(
                    new TokenResponse(null, null, 0L, null, null, "SESSION_EXPIRED"));
        }

        Optional<UserResponse> cached = profileCache.get(userId);
        UserResponse user;
        if (cached.isPresent()) {
            user = cached.get();
        } else {
            user = userMicroservice.findProfile(userId)
                    .orElseThrow(() -> new NotFoundException("User not found", "USER_NOT_FOUND"));
            profileCache.set(user);
        }

        // Adiciona o jti antigo na denylist
        String oldJti = token.getClaimAsString("jti");
        if (oldJti != null) {
            Instant oldExp = Instant.ofEpochSecond(token.getExpiresAt().getEpochSecond());
            Duration ttl = Duration.between(Instant.now(), oldExp);
            if (ttl.isPositive()) {
                try {
                    redisTemplate.opsForValue().set(
                            "auth:denylist:" + oldJti,
                            "logged_out",
                            ttl);
                } catch (Exception e) {
                    logger.warn("Failed to add old token to denylist", e);
                    return ResponseEntity.status(503).body(
                            new TokenResponse(null, null, 0L, null, null, "REDIS_UNAVAILABLE"));
                }
            }
        }

        // Emite novo token com mesmo authTime
        var newToken = tokenIssuer.issueForUser(user, user.roles(), authTime);
        Instant newExpiresAt = newToken.expiresAt();

        TokenResponse response = new TokenResponse(
                newToken.value(),
                "Bearer",
                3600L,
                newExpiresAt,
                authTime.plus(SESSION_MAX_AGE),
                null);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt token) {
        String jti = token.getClaimAsString("jti");
        if (jti != null) {
            Instant expiresAt = Instant.ofEpochSecond(token.getExpiresAt().getEpochSecond());
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (ttl.isPositive()) {
                try {
                    redisTemplate.opsForValue().set(
                            "auth:denylist:" + jti,
                            "logged_out",
                            ttl);
                } catch (Exception e) {
                    logger.warn("Failed to add token to denylist", e);
                    return ResponseEntity.status(503).build();
                }
            }
        }
        return ResponseEntity.noContent().build();
    }

    public record SessionResponse(
            Long id,
            String name,
            String email,
            String photoUrl,
            java.util.List<String> roles,
            Instant expiresAt) {}

    public record TokenResponse(
            String accessToken,
            String tokenType,
            Long expiresIn,
            Instant expiresAt,
            Instant sessionExpiresAt,
            String code) {}
}
