package ecommerce_event_driven.shipment.shared.web;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}
