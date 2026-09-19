package ecommerce_event_driven.payment.modules.payment.application.usecases;

import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Meios de pagamento aceitos, espelhados do provedor. A lista muda raramente, entao fica
 * 6 h no Redis; Redis fora nunca derruba a rota, so faz ir direto ao provedor.
 */
@Service
public class GetPaymentMethodsUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetPaymentMethodsUseCase.class);
    private static final String KEY = "payment:methods:mp";
    private static final Duration TTL = Duration.ofHours(6);

    private final PaymentGatewayPort paymentGateway;
    private final StringRedisTemplate redis;
    private final JsonMapper json;

    public GetPaymentMethodsUseCase(PaymentGatewayPort paymentGateway, StringRedisTemplate redis, JsonMapper json) {
        this.paymentGateway = paymentGateway;
        this.redis = redis;
        this.json = json;
    }

    public JsonNode execute() {
        try {
            String cached = redis.opsForValue().get(KEY);
            if (cached != null) {
                return json.readTree(cached);
            }
        } catch (RuntimeException e) {
            log.warn("Redis indisponivel lendo {}: {}", KEY, e.getMessage());
        }

        JsonNode methods = paymentGateway.getPaymentMethods();

        try {
            redis.opsForValue().set(KEY, json.writeValueAsString(methods), TTL);
        } catch (RuntimeException e) {
            log.warn("Redis indisponivel gravando {}: {}", KEY, e.getMessage());
        }
        return methods;
    }
}
