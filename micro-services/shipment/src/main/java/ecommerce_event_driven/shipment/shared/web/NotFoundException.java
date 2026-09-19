package ecommerce_event_driven.shipment.shared.web;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
