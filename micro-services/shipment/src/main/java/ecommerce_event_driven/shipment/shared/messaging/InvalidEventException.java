package ecommerce_event_driven.shipment.shared.messaging;

/**
 * Excecao lancada durante validacao de evento. Causa envio direto para DLT.
 * Nao deve ser retentada, pois o problema e no payload, nao na operacao.
 */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
