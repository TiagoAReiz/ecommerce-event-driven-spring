package ecommerce_event_driven.shipment.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Excecao base para erros de API. Todas as excecoes derivadas
 * retornam um HttpStatus estavel e um codigo para o cliente.
 */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(String message, Throwable cause, HttpStatus status, String code) {
        super(message, cause);
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
