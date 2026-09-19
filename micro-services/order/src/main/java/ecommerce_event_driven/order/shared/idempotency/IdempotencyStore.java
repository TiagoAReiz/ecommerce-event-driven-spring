package ecommerce_event_driven.order.shared.idempotency;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Armazena estado de idempotencia em Redis.
 * Chave: idem:orders:{sub}:{Idempotency-Key}
 *
 * Estados:
 * - IN_FLIGHT: requisicao em processamento (TTL 60s)
 * - {bodyHash,status,body}: resultado (TTL 24h)
 *
 * Se Redis estiver fora, loga WARN e retorna null, permitindo que a transacao prossiga sem idempotencia.
 */
@Component
public class IdempotencyStore {
    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyStore.class);
    private static final Duration IN_FLIGHT_TTL = Duration.ofSeconds(60);
    private static final Duration RESULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final JsonMapper mapper;

    public IdempotencyStore(StringRedisTemplate redis, JsonMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /**
     * Marca a chave como IN_FLIGHT.
     * Retorna true se conseguiu marcar (ninguem estava processando), false se ja estava em voo.
     */
    public boolean markInFlight(Long userId, String idempotencyKey) {
        try {
            String key = buildKey(userId, idempotencyKey);
            Boolean set = redis.opsForValue().setIfAbsent(key, "IN_FLIGHT", IN_FLIGHT_TTL);
            return set != null && set;
        } catch (Exception e) {
            LOG.warn("Redis indisponivel ao marcar IN_FLIGHT: {}", e.getMessage());
            return true;  // Sem Redis, permite prosseguir
        }
    }

    /**
     * Grava o resultado de uma requisicao idempotente.
     */
    public void storeResult(Long userId, String idempotencyKey, String bodyHash, int status, String responseBody) {
        try {
            String key = buildKey(userId, idempotencyKey);
            var result = new IdempotencyResult(bodyHash, status, responseBody);
            String json = mapper.writeValueAsString(result);
            redis.opsForValue().set(key, json, RESULT_TTL);
        } catch (Exception e) {
            LOG.warn("Redis indisponivel ao gravar resultado: {}", e.getMessage());
        }
    }

    /**
     * Recupera resultado armazenado de uma requisicao idempotente.
     * Retorna null se nao existe ou se a chave eh IN_FLIGHT.
     */
    /**
     * Remove a marca IN_FLIGHT depois de uma falha: sem isso, o retry legitimo do cliente
     * ficaria preso em 409 ate o TTL da marca vencer.
     */
    public void release(Long userId, String idempotencyKey) {
        try {
            redis.delete(buildKey(userId, idempotencyKey));
        } catch (Exception e) {
            LOG.warn("Redis indisponivel ao liberar chave de idempotencia: {}", e.getMessage());
        }
    }

    public IdempotencyResult getResult(Long userId, String idempotencyKey) {
        try {
            String key = buildKey(userId, idempotencyKey);
            String value = redis.opsForValue().get(key);

            if (value == null) {
                return null;  // Nao existe
            }
            if ("IN_FLIGHT".equals(value)) {
                return null;  // Ainda em processamento
            }

            return mapper.readValue(value, IdempotencyResult.class);
        } catch (Exception e) {
            LOG.warn("Erro ao recuperar resultado de idempotencia: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calcula hash SHA256 do corpo da requisicao.
     */
    public String hash(String body) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            LOG.error("Erro ao calcular hash: {}", e.getMessage());
            throw new RuntimeException("Falha ao calcular hash", e);
        }
    }

    /**
     * Valida se o Idempotency-Key e um UUID valido.
     */
    public boolean isValidUuid(String key) {
        try {
            UUID.fromString(key);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String buildKey(Long userId, String idempotencyKey) {
        return String.format("idem:orders:%d:%s", userId, idempotencyKey);
    }

    public record IdempotencyResult(
            String bodyHash,
            Integer status,
            String body
    ) {}
}
