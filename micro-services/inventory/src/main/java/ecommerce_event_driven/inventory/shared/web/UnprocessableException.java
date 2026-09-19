package ecommerce_event_driven.inventory.shared.web;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends ApiException {

    public UnprocessableException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_ENTITY");
    }
}
