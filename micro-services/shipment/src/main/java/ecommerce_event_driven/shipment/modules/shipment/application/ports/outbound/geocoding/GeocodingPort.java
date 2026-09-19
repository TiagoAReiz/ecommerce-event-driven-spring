package ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.geocoding;

/**
 * Port para geocodificacao de CEPs.
 * Ordem: cache Redis -> BrasilAPI -> Nominatim
 */
public interface GeocodingPort {
    LocationDto locate(String zipcode);
}
