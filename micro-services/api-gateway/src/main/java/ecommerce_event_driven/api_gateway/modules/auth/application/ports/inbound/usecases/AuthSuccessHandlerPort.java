package ecommerce_event_driven.api_gateway.modules.auth.application.ports.inbound.usecases;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

public interface AuthSuccessHandlerPort {
    void onAuthenticationSuccess(HttpServletRequest http, HttpServletResponse httpResponse, Authentication authentication);
}
