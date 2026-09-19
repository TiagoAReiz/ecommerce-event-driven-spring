package ecommerce_event_driven.inventory.config;

/**
 * Excecao lancada quando um evento e invalido e nao pode ser processado.
 * Faz o evento ir direto para a DLT sem retry.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
