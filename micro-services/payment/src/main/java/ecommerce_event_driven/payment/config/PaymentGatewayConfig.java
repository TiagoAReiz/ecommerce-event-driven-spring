package ecommerce_event_driven.payment.config;

import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.gateway.PaymentGatewayPort;
import ecommerce_event_driven.payment.modules.payment.infra.outbound.gateway.FakePaymentGateway;
import ecommerce_event_driven.payment.modules.payment.infra.outbound.gateway.MercadoPagoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Escolhe o provedor de pagamento pela presenca do access token.
 *
 * <p>Decisao explicita em vez de @ConditionalOnProperty: a anotacao nao distingue
 * "vazio" de "preenchido" de forma confiavel, e errar aqui deixa o servico sem nenhum
 * provedor ou com os dois.
 */
@Configuration
public class PaymentGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayConfig.class);

    @Bean
    public PaymentGatewayPort paymentGateway(
            @Value("${app.mercadopago.base-url}") String baseUrl,
            @Value("${app.mercadopago.access-token:}") String accessToken) {
        if (StringUtils.hasText(accessToken)) {
            return new MercadoPagoClient(baseUrl, accessToken);
        }
        log.warn("MP_ACCESS_TOKEN vazio: modo fake do Mercado Pago, toda cobranca e aprovada na hora");
        return new FakePaymentGateway();
    }
}
