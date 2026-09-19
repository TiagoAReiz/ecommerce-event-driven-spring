package ecommerce_event_driven.api_gateway.modules.auth.application.usecases;

import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.CreateUserRequest;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.IssuedToken;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.UserResponse;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.inbound.usecases.AuthSuccessHandlerPort;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.external.UserMicroservicePort;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security.TokenIssuerPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Roda depois que o Google confirma o login.
 *
 * <p>Busca o usuario no microservico user, cria se for o primeiro acesso,
 * assina o token e devolve o browser para o front.
 */
@Service
public class AuthSuccessHandler implements AuthSuccessHandlerPort {

    private final UserMicroservicePort userMicroservice;
    private final TokenIssuerPort tokenIssuer;
    private final String frontUrl;

    public AuthSuccessHandler(
            UserMicroservicePort userMicroservice,
            TokenIssuerPort tokenIssuer,
            @Value("${app.front-url}") String frontUrl) {
        this.userMicroservice = userMicroservice;
        this.tokenIssuer = tokenIssuer;
        this.frontUrl = frontUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest http, HttpServletResponse httpResponse, Authentication authentication) {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String googleSub = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");

        UserResponse user = getOrCreate(googleSub, email, oAuth2User.getAttribute("name"), oAuth2User.getAttribute("picture"));
        IssuedToken token = tokenIssuer.issueForUser(user);

            try {
                httpResponse.sendRedirect(frontUrl + "/callback?token="
                        + URLEncoder.encode(token.value(), StandardCharsets.UTF_8));

            }catch (IOException e){
                throw new RuntimeException(e);
            }

    }

    private UserResponse getOrCreate(String googleSub, String email, String name, String photoUrl) {
        UserResponse user = userMicroservice.findByEmail(email)
                .orElseGet(() -> userMicroservice.create(
                        new CreateUserRequest(name, email, googleSub, photoUrl)));

        // A busca e por email, mas quem identifica a conta e o sub do Google.
        // Se os dois nao batem, o email foi reaproveitado por outra conta: e
        // melhor estourar do que logar alguem na conta errada.
        if (!googleSub.equals(user.googleSub())) {
            throw new IllegalStateException("email " + email + " ja pertence a outra conta Google");
        }
        return user;
    }
}
