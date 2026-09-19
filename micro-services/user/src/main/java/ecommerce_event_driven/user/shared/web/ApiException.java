package ecommerce_event_driven.user.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Excecao base para erros de API. Subclasses mapeiam para codigos HTTP especificos
 * e codigos de erro estaveis para o front ramificar.
 */
public abstract class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public ApiException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
