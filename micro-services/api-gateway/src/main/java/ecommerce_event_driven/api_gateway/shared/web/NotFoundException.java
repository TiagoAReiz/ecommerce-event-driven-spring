package ecommerce_event_driven.api_gateway.shared.web;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public NotFoundException(String message, String code) {
        super(message, HttpStatus.NOT_FOUND, code);
    }
}
