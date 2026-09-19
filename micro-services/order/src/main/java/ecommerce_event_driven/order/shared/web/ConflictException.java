package ecommerce_event_driven.order.shared.web;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public ConflictException(String code, String message, Throwable cause) {
        super(HttpStatus.CONFLICT, code, message, cause);
    }
}
