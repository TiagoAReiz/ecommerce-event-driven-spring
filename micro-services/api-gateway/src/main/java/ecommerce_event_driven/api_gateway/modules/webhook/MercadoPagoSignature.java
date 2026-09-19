package ecommerce_event_driven.api_gateway.modules.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Valida a assinatura HMAC-SHA256 de webhooks do Mercado Pago.
 *
 * Manifest: id:<data.id>;request-id:<x-request-id>;ts:<ts>;
 * HMAC-SHA256(manifest, webhook-secret) comparado em tempo constante.
 */
public class MercadoPagoSignature {

    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoSignature.class);
    private static final long MAX_AGE_SECONDS = 5 * 60; // 5 minutos

    private final String webhookSecret;

    public MercadoPagoSignature(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * Valida a assinatura do webhook.
     *
     * @param dataId valor de data.id
     * @param requestId valor de x-request-id
     * @param timestamp valor de ts (em segundos)
     * @param signature valor de v1 do header x-signature
     * @return true se valido, false se invalido
     * @throws IllegalArgumentException se timestamp estiver fora da janela de 5 minutos
     */
    public boolean validate(String dataId, String requestId, String timestamp, String signature) {
        // Valida timestamp (janela de 5 minutos)
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            logger.warn("Invalid timestamp format: {}", timestamp);
            return false;
        }

        Instant now = Instant.now();
        long age = now.getEpochSecond() - ts;
        if (Math.abs(age) > MAX_AGE_SECONDS) {
            throw new IllegalArgumentException("Timestamp out of 5-minute window");
        }

        // Monta o manifest
        String manifest = String.format(
                "id:%s;request-id:%s;ts:%s;",
                dataId,
                requestId,
                timestamp);

        // Calcula HMAC-SHA256
        String calculated = calculateHmacSha256(manifest, webhookSecret);

        // Compara em tempo constante
        return constantTimeEquals(calculated, signature);
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(data.getBytes());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate HMAC", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digestA = md.digest(a.getBytes());
            byte[] digestB = md.digest(b.getBytes());
            return MessageDigest.isEqual(digestA, digestB);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
