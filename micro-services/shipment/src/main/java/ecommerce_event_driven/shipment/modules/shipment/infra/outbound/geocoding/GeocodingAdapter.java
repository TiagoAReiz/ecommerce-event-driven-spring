package ecommerce_event_driven.shipment.modules.shipment.infra.outbound.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.geocoding.GeocodingPort;
import ecommerce_event_driven.shipment.modules.shipment.application.ports.outbound.geocoding.LocationDto;
import ecommerce_event_driven.shipment.shared.web.NotFoundException;
import ecommerce_event_driven.shipment.shared.web.UnprocessableException;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Geocodificacao com fallback: Redis -> BrasilAPI -> Nominatim
 */
@Component
public class GeocodingAdapter implements GeocodingPort {
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeocodingAdapter(StringRedisTemplate redisTemplate, RestClient restClient, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public LocationDto locate(String zipcode) {
        // 1. Tentar cache Redis (30 dias)
        String cached = getCachedLocation(zipcode);
        if (cached != null) {
            return parseLocationJson(cached, zipcode);
        }

        // 2. Tentar BrasilAPI
        LocationDto location = locateViaBrasilAPI(zipcode);
        if (location != null && location.latitude() != null && location.longitude() != null) {
            cacheLocation(zipcode, location);
            return location;
        }

        // 3. Se BrasilAPI nao tem coordenada, tentar Nominatim
        if (location != null) {
            // BrasilAPI retornou o CEP mas sem coordenadas
            LocationDto locationWithCoords = locateViaNominatim(location.city(), location.state());
            if (locationWithCoords != null) {
                LocationDto complete = new LocationDto(
                        zipcode,
                        location.city(),
                        location.state(),
                        locationWithCoords.latitude(),
                        locationWithCoords.longitude());
                cacheLocation(zipcode, complete);
                return complete;
            }
            // Nominatim tambem falhou
            throw new UnprocessableException("ZIPCODE_NOT_GEOCODED");
        }

        // CEP nao existe
        throw new NotFoundException("ZIPCODE_NOT_FOUND");
    }

    private String getCachedLocation(String zipcode) {
        try {
            return redisTemplate.opsForValue().get("geo:cep:" + zipcode);
        } catch (Exception ex) {
            // Redis fora: log WARN e segue
            System.out.println("WARN: Redis falhou ao ler cache de " + zipcode);
            return null;
        }
    }

    private void cacheLocation(String zipcode, LocationDto location) {
        try {
            String json = objectMapper.writeValueAsString(location);
            redisTemplate.opsForValue().set("geo:cep:" + zipcode, json, 30, TimeUnit.DAYS);
        } catch (Exception ex) {
            // Redis fora: log WARN e segue
            System.out.println("WARN: Redis falhou ao cachear " + zipcode);
        }
    }

    private LocationDto locateViaBrasilAPI(String zipcode) {
        try {
            String response = restClient.get()
                    .uri("https://brasilapi.com.br/api/cep/v2/{cep}", zipcode)
                    .retrieve()
                    .body(String.class);

            if (response == null) {
                return null;
            }

            JsonNode node = objectMapper.readTree(response);
            String city = node.get("city").asText();
            String state = node.get("state").asText();

            Double latitude = null;
            Double longitude = null;

            if (node.has("location") && node.get("location").has("coordinates")) {
                JsonNode coords = node.get("location").get("coordinates");
                if (coords.has("longitude") && coords.has("latitude")) {
                    longitude = coords.get("longitude").asDouble();
                    latitude = coords.get("latitude").asDouble();
                }
            }

            return new LocationDto(zipcode, city, state, latitude, longitude);
        } catch (Exception ex) {
            // BrasilAPI 404 ou erro: retorna null
            return null;
        }
    }

    private LocationDto locateViaNominatim(String city, String state) {
        try {
            String response = restClient.get()
                    .uri("https://nominatim.openstreetmap.org/search?city={city}&state={state}&country=Brazil&format=json&limit=1",
                            city, state)
                    .header("User-Agent", "ecommerce-event-driven/1.0")
                    .retrieve()
                    .body(String.class);

            if (response == null || response.equals("[]")) {
                return null;
            }

            JsonNode array = objectMapper.readTree(response);
            if (array.isEmpty()) {
                return null;
            }

            JsonNode first = array.get(0);
            Double latitude = Double.parseDouble(first.get("lat").asText());
            Double longitude = Double.parseDouble(first.get("lon").asText());

            return new LocationDto(null, city, state, latitude, longitude);
        } catch (Exception ex) {
            // Nominatim falhou
            return null;
        }
    }

    private LocationDto parseLocationJson(String json, String zipcode) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new LocationDto(
                    zipcode,
                    node.get("city").asText(),
                    node.get("state").asText(),
                    node.get("latitude").asDouble(),
                    node.get("longitude").asDouble());
        } catch (Exception ex) {
            return null;
        }
    }
}
