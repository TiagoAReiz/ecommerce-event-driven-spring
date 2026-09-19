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
                .method(entity.getMethod())
                .statusDetail(entity.getStatusDetail())
                .qrCode(entity.getQrCode())
                .qrCodeBase64(entity.getQrCodeBase64())
                .ticketUrl(entity.getTicketUrl())
                .initPoint(entity.getInitPoint())
                .expiresAt(entity.getExpiresAt())
                .cardBrand(entity.getCardBrand())
                .cardLast4(entity.getCardLast4())
                .installments(entity.getInstallments())
                .refundedAmount(entity.getRefundedAmount())
                .approvedAt(entity.getApprovedAt())
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
                .method(model.method())
                .statusDetail(model.statusDetail())
                .qrCode(model.qrCode())
                .qrCodeBase64(model.qrCodeBase64())
                .ticketUrl(model.ticketUrl())
                .initPoint(model.initPoint())
                .expiresAt(model.expiresAt())
                .cardBrand(model.cardBrand())
                .cardLast4(model.cardLast4())
                .installments(model.installments())
                .refundedAmount(model.refundedAmount())
                .approvedAt(model.approvedAt())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
