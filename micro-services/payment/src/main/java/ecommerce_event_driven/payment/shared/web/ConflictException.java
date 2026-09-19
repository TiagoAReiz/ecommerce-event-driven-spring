package ecommerce_event_driven.payment.shared.web;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String message, String code) {
        super(message, HttpStatus.CONFLICT, code);
    }
}
