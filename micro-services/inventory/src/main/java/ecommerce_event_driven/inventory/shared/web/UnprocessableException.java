package ecommerce_event_driven.inventory.shared.web;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends ApiException {

    public UnprocessableException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_ENTITY");
    }

    // Code estavel por caso: o front decide o texto pelo code, nao por mensagem livre.
    public UnprocessableException(String message, String code) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}
