package ecommerce_event_driven.shipment.shared.web;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT, "CONFLICT");
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause, HttpStatus.CONFLICT, "CONFLICT");
    }
}
