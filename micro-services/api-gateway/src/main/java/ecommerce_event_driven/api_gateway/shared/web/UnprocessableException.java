package ecommerce_event_driven.api_gateway.shared.web;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends ApiException {

    public UnprocessableException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_ENTITY");
    }

    public UnprocessableException(String message, String code) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}
