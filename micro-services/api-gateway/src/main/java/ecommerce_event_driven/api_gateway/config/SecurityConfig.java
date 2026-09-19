package ecommerce_event_driven.api_gateway.config;

import ecommerce_event_driven.api_gateway.modules.auth.application.ports.inbound.usecases.AuthSuccessHandlerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthSuccessHandlerPort authSuccessHandlerPort;

    public SecurityConfig(final AuthSuccessHandlerPort authSuccessHandlerPort) {
        this.authSuccessHandlerPort = authSuccessHandlerPort;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        // Os outros microservicos buscam a chave publica aqui, sem
                        // token nenhum: e por ela que eles validam token.
                        .requestMatchers("/.well-known/jwks.json").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(authSuccessHandlerPort::onAuthenticationSuccess)
                );
        return http.build();
    }
}
