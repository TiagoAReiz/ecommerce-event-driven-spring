package ecommerce_event_driven.payment.shared.web;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends ApiException {
    public UnprocessableException(String message, String code) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}
