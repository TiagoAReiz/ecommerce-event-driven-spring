package ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.geocoding;

/**
 * Localizacao de um CEP com coordenadas.
 */
public record LocationDto(
        String zipcode,
        String city,
        String state,
        Double latitude,
        Double longitude) {}
