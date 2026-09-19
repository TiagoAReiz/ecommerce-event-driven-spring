package ecommerce_event_driven.api_gateway.shared.web;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "CONFLICT");
    }

    public ConflictException(String message, String code) {
        super(message, HttpStatus.CONFLICT, code);
    }
}
