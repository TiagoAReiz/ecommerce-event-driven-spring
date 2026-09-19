package ecommerce_event_driven.api_gateway.config;

import ecommerce_event_driven.api_gateway.modules.auth.application.ports.inbound.usecases.AuthSuccessHandlerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthSuccessHandlerPort authSuccessHandlerPort;

    public SecurityConfig(final AuthSuccessHandlerPort authSuccessHandlerPort) {
        this.authSuccessHandlerPort = authSuccessHandlerPort;
    }

    /**
     * Cadeia para /api/v1/**, /auth/**, /public/**, /.well-known/**.
     * Resource server JWT com validacao de aud=front.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, JwtDecoder browserJwtDecoder) throws Exception {
        http
                .securityMatcher("/api/v1/**", "/auth/**", "/public/**", "/.well-known/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {}) // CORS configurado em WebConfig
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        // Rotas publicas (sem token)
                        .requestMatchers("/.well-known/jwks.json").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/service-token").permitAll()
                        .requestMatchers(HttpMethod.POST, "/public/webhooks/**").permitAll()
                        // Rotas do proprio usuario e de gestao casariam com os padroes publicos
                        // abaixo (/users/{id}, /products/**): precisam vir antes e exigir token.
                        .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                        .requestMatchers("/api/v1/products/manage", "/api/v1/products/manage/**").authenticated()
                        // Rotas publicas da API (GET apenas, com token de servico interno)
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shipping/quote").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/config").permitAll()
                        // Tudo mais precisa de autenticacao
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(browserJwtDecoder)));

        return http.build();
    }

    /**
     * Cadeia para /oauth2/** e /login/**.
     * Login OAuth2 com sessao HTTP.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain loginChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/**")
                .csrf(csrf -> {}) // Padrao: CSRF habilitado para formularios
                .sessionManagement(session -> session.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(authSuccessHandlerPort::onAuthenticationSuccess));

        return http.build();
    }
}
