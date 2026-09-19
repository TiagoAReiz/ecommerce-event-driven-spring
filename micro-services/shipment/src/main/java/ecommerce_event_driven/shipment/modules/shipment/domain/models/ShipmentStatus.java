package ecommerce_event_driven.shipment.modules.shipment.domain.models;

/**
 * Espelha o tipo ENUM shipment_status do Postgres.
 * As constantes ficam minusculas de proposito: o Hibernate grava name() e o
 * valor precisa bater exatamente com o label declarado na migration.
 */
public enum ShipmentStatus {
    pending,
    ready_to_ship,
    in_transit,
    out_for_delivery,
    delivered,
    returned,
    cancelled
}
