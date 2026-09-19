package ecommerce_event_driven.inventory.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Excecao base para erros da API. Subclasses definem status HTTP e code estavel.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String code;

    protected ApiException(String message, HttpStatus httpStatus, String code) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    protected ApiException(String message, Throwable cause, HttpStatus httpStatus, String code) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }
}
