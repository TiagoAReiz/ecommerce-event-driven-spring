package ecommerce_event_driven.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Este servico so valida token, nunca emite. A chave publica vem do JWKS do
 * gateway, e a propriedade audiences=internal garante que o token do browser
 * nao vale aqui dentro.
 *
 * <p>Os escopos vem da claim scope do token, que o Spring converte em
 * authority com o prefixo SCOPE_. As regras por rota entram aqui junto com os
 * controllers, seguindo o catalogo de escopos de docs/api-contracts.md.
 *
 * <p>Os listeners Kafka nao passam por esta cadeia: ela so protege HTTP.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // API de Bearer puro: sem sessao e sem cookie, nao existe vetor
                // de CSRF para o filtro proteger.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
