package ecommerce_event_driven.payment.modules.payment.application.mappers;

import ecommerce_event_driven.payment.modules.payment.domain.models.PaymentStatus;

public class MercadoPagoStatusMapper {
    public static PaymentStatus mapMercadoPagoStatus(String mpStatus) {
        return switch (mpStatus) {
            case "pending", "in_process", "in_mediation" -> PaymentStatus.pending;
            case "authorized" -> PaymentStatus.authorized;
            case "approved" -> PaymentStatus.captured;
            case "rejected", "cancelled" -> PaymentStatus.failed;
            case "refunded", "charged_back" -> PaymentStatus.refunded;
            default -> PaymentStatus.pending;
        };
    }
}
