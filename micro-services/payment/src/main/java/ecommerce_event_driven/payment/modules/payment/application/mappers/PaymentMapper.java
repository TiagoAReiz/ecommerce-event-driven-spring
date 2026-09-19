package ecommerce_event_driven.payment.modules.payment.application.mappers;

import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.modules.payment.infra.outbound.repos.entity.PaymentEntity;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static Payment toDomain(PaymentEntity entity) {
        if (entity == null) {
            return null;
        }
        return Payment.builder()
                .id(entity.getId())
                .idOrder(entity.getIdOrder())
                .value(entity.getValue())
                .status(entity.getStatus())
                .provider(entity.getProvider())
                .externalId(entity.getExternalId())
                .idempotencyKey(entity.getIdempotencyKey())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static PaymentEntity toEntity(Payment model) {
        if (model == null) {
            return null;
        }
        return PaymentEntity.builder()
                .id(model.id())
                .idOrder(model.idOrder())
                .value(model.value())
                .status(model.status())
                .provider(model.provider())
                .externalId(model.externalId())
                .idempotencyKey(model.idempotencyKey())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
