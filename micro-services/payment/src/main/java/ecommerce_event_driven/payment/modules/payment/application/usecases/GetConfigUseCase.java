package ecommerce_event_driven.payment.modules.payment.application.usecases;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GetConfigUseCase {
    private final String publicKey;
    private final boolean fakeMode;

    public GetConfigUseCase(@Value("${app.mercadopago.public-key}") String publicKey,
            @Value("${app.mercadopago.access-token:}") String accessToken) {
        this.publicKey = publicKey;
        this.fakeMode = accessToken == null || accessToken.isBlank();
    }

    public PaymentConfigResponse execute() {
        String environment = fakeMode ? "fake" : "sandbox";
        return new PaymentConfigResponse(
                "mercadopago",
                publicKey,
                "pt-BR",
                "BRL",
                environment,
                new String[] {"pix", "credit_card", "checkout_pro"});
    }

    public record PaymentConfigResponse(
            String provider,
            String publicKey,
            String locale,
            String currency,
            String environment,
            String[] enabledMethods) {
    }
}
