package ecommerce_event_driven.payment.config;

public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
