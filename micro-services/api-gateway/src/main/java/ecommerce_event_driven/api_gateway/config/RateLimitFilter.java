package ecommerce_event_driven.api_gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

/**
 * Rate limit de 120 requisicoes por minuto por IP em /api/v1/**.
 *
 * Usa Redis com chave rl:{ip}:{epochMinute}. Se Redis falha, deixa passar.
 * Responde 429 com header Retry-After quando limite e ultrapassado.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int LIMIT_PER_MINUTE = 120;
    private static final long WINDOW_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        long epochMinute = Instant.now().getEpochSecond() / 60;
        String key = "rl:" + clientIp + ":" + epochMinute;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);

            if (count != null && count > LIMIT_PER_MINUTE) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", "60");
                response.getWriter().write("{\"type\":\"urn:ietf:rfc:9457\",\"title\":\"Rate limit exceeded\",\"status\":429,\"detail\":\"Maximum 120 requests per minute\",\"code\":\"RATE_LIMIT_EXCEEDED\"}");
                return;
            }
        } catch (Exception e) {
            // Falha de Redis: deixa passar com warning
            logger.warn("Rate limit check failed, passing request", e);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
