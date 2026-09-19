package ecommerce_event_driven.shipment.modules.shipment.application.usecases;

import ecommerce_event_driven.shipment.config.StoreOriginProperties;
import ecommerce_event_driven.shipment.modules.shipment.application.dtos.ShippingQuoteResponse;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.geocoding.GeocodingPort;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.geocoding.LocationDto;
import ecommerce_event_driven.shipment.shared.web.BadRequestException;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Calcula o frete para um destino baseado em distancia.
 */
@Service
public class CalculateShippingQuoteService {
    private final GeocodingPort geocoding;
    private final StoreOriginProperties origin;
    private final BigDecimal ratePerKm;

    public CalculateShippingQuoteService(
            GeocodingPort geocoding,
            StoreOriginProperties origin,
            @Value("${app.shipping.rate-per-km}") BigDecimal ratePerKm) {
        this.geocoding = geocoding;
        this.origin = origin;
        this.ratePerKm = ratePerKm;
    }

    public ShippingQuoteResponse execute(String zipcode) {
        validateZipcode(zipcode);

        LocationDto destination = geocoding.locate(zipcode);

        int distanceKm = calculateDistance(
                origin.getLatitude(), origin.getLongitude(),
                destination.latitude(), destination.longitude());

        BigDecimal freightCost = BigDecimal.valueOf(distanceKm).multiply(ratePerKm);

        return new ShippingQuoteResponse(
                zipcode,
                new ShippingQuoteResponse.Origin(origin.getCity(), origin.getState()),
                distanceKm,
                ratePerKm,
                freightCost);
    }

    private void validateZipcode(String zipcode) {
        if (zipcode == null || zipcode.length() != 8 || !zipcode.matches("\\d+")) {
            throw new BadRequestException("zipcode deve ter 8 digitos numericos");
        }
    }

    private int calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        double distance = earthRadius * c;
        return (int) Math.ceil(distance);
    }
}
