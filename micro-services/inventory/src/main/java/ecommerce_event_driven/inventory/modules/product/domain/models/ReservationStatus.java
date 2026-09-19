package ecommerce_event_driven.inventory.modules.product.domain.models;

/**
 * Espelha o tipo ENUM reservation_status do Postgres.
 * As constantes ficam minusculas de proposito: o Hibernate grava name() e o
 * valor precisa bater exatamente com o label declarado na migration.
 */
public enum ReservationStatus {
    held,
    confirmed,
    released
}
