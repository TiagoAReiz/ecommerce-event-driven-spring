package ecommerce_event_driven.api_gateway.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Excecao base para todos os erros de negocio e validacao mapeados para ProblemDetail.
 * Subclasses definem codigo HTTP e mensagem estavel para o front.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
