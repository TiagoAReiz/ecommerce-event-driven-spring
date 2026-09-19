package ecommerce_event_driven.payment.shared.web;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {
    public BadRequestException(String message, String code) {
        super(message, HttpStatus.BAD_REQUEST, code);
    }
}
