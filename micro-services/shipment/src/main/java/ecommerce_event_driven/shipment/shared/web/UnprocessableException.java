package ecommerce_event_driven.shipment.shared.web;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends ApiException {
    public UnprocessableException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE");
    }

    public UnprocessableException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE");
    }
}
