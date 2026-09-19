package ecommerce_event_driven.payment.shared.web;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String code;

    public ApiException(String message, HttpStatus httpStatus, String code) {
        super(message);
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
