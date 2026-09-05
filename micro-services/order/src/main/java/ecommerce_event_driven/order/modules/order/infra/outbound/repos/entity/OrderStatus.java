package ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity;

/**
 * Espelha o tipo ENUM order_status do Postgres.
 * As constantes ficam minusculas de proposito: o Hibernate grava name() e o
 * valor precisa bater exatamente com o label declarado na migration.
 */
public enum OrderStatus {
    pending,
    paid,
    processing,
    shipped,
    delivered,
    cancelled,
    refunded
}
