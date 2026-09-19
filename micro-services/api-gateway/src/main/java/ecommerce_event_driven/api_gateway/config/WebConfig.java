package ecommerce_event_driven.api_gateway.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuracoes de CORS para o gateway.
 *
 * Origem: app.front-url
 * Metodos: GET, POST, PUT, DELETE, PATCH, OPTIONS
 * Headers expostos: Location, ETag, X-Request-Id, Idempotency-Replayed
 */
@Configuration
public class WebConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.front-url}") String frontUrl) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(frontUrl));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList(
                "Location",
                "ETag",
                "X-Request-Id",
                "Idempotency-Replayed",
                "Retry-After",
                "RateLimit-Limit",
                "RateLimit-Remaining",
                "RateLimit-Reset"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
