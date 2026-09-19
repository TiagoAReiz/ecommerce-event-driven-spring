package ecommerce_event_driven.order.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Falha de um servico chamado pelo order: 503 quando ele esta fora, 504 no timeout e
 * 502 quando respondeu algo que nao da para usar. Nunca 400: o pedido do cliente estava certo.
 */
public class UpstreamException extends ApiException {

    public UpstreamException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
