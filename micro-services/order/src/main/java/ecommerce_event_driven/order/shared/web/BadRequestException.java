package ecommerce_event_driven.order.shared.web;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {
    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestException(String code, String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, code, message, cause);
    }
}
