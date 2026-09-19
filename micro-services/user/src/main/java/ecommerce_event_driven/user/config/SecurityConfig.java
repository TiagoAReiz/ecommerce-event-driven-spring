package ecommerce_event_driven.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
 * authority com o prefixo SCOPE_. Ordem importa: /users/me/** antes de /users/{id}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // Rotas publicas (usuarios)
                        .requestMatchers(HttpMethod.GET, "/users").hasAuthority("SCOPE_users:read")
                        .requestMatchers(HttpMethod.POST, "/users").hasAuthority("SCOPE_users:write")

                        // Perfil proprio (ANTES de /users/{id})
                        .requestMatchers(HttpMethod.GET, "/users/me").hasAuthority("SCOPE_users:read")
                        .requestMatchers(HttpMethod.PATCH, "/users/me").hasAuthority("SCOPE_users:write")
                        .requestMatchers(HttpMethod.DELETE, "/users/me").hasAuthority("SCOPE_users:write")

                        // Perfil publico
                        .requestMatchers(HttpMethod.GET, "/users/{id}").hasAuthority("SCOPE_users:read")

                        // Enderecos (ANTES de /internal/**)
                        .requestMatchers(HttpMethod.GET, "/users/me/addresses").hasAuthority("SCOPE_addresses:read")
                        .requestMatchers(HttpMethod.GET, "/users/me/addresses/{id}").hasAuthority("SCOPE_addresses:read")
                        .requestMatchers(HttpMethod.POST, "/users/me/addresses").hasAuthority("SCOPE_addresses:write")
                        .requestMatchers(HttpMethod.PUT, "/users/me/addresses/{id}").hasAuthority("SCOPE_addresses:write")
                        .requestMatchers(HttpMethod.PATCH, "/users/me/addresses/{id}").hasAuthority("SCOPE_addresses:write")
                        .requestMatchers(HttpMethod.DELETE, "/users/me/addresses/{id}").hasAuthority("SCOPE_addresses:write")

                        // Rotas internas servidor-a-servidor
                        .requestMatchers("/internal/**").hasAuthority("SCOPE_internal:hydrate")

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // API de Bearer puro: sem sessao e sem cookie, nao existe vetor
                // de CSRF para o filtro proteger.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
