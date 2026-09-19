package ecommerce_event_driven.payment.modules.payment.domain.models;

/**
 * Espelha o tipo ENUM payment_status do Postgres.
 * As constantes ficam minusculas de proposito: o Hibernate grava name() e o
 * valor precisa bater exatamente com o label declarado na migration.
 */
public enum PaymentStatus {
    pending,
    authorized,
    captured,
    failed,
    refunded,
    cancelled
}
