package ecommerce_event_driven.order.shared.web;

import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * Excecao base para erros de negocio e validacao. Mapeia para ProblemDetail
 * com status HTTP estavel e code legivel para o cliente.
 */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public ProblemDetail toProblemDetail() {
        return new ProblemDetail(
                "https://api.loja.dev/errors/" + code,
                code,
                status.value(),
                getMessage(),
                Instant.now()
        );
    }

    public record ProblemDetail(
            String type,
            String code,
            Integer status,
            String detail,
            Instant timestamp
    ) {}
}
