package ecommerce_event_driven.api_gateway.modules.auth.infra.outbound.cache;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.UserResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache de perfil em Redis com fallback ao microservico user.
 *
 * <p>TTL: 5 minutos. Falha de Redis deixa passar normalmente.
 */
@Component
public class ProfileCache {

    private static final Logger logger = LoggerFactory.getLogger(ProfileCache.class);
    private static final String KEY_PREFIX = "auth:profile:";
    private static final long TTL_SECONDS = 300;

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public ProfileCache(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    public Optional<UserResponse> get(Long userId) {
        String key = KEY_PREFIX + userId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                UserResponse profile = jsonMapper.readValue(cached, UserResponse.class);
                logger.debug("Profile cache hit for user {}", userId);
                return Optional.of(profile);
            }
        } catch (Exception e) {
            logger.warn("Profile cache read error for user {}", userId, e);
        }
        return Optional.empty();
    }

    public void set(UserResponse profile) {
        String key = KEY_PREFIX + profile.id();
        try {
            String json = jsonMapper.writeValueAsString(profile);
            redisTemplate.opsForValue().set(key, json, java.time.Duration.ofSeconds(TTL_SECONDS));
            logger.debug("Profile cached for user {}", profile.id());
        } catch (Exception e) {
            logger.warn("Profile cache write error for user {}", profile.id(), e);
            // Falha de cache: continua normalmente
        }
    }
}
