package ecommerce_event_driven.order.shared.web;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends ApiException {
    public UnprocessableException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public UnprocessableException(String code, String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message, cause);
    }
}
