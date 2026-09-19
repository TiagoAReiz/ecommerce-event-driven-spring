package ecommerce_event_driven.api_gateway.shared.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Converte todas as excecoes para ProblemDetail (RFC 9457).
 * Acrescenta campos extra: code, requestId, timestamp e errors[] para validacao.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, WebRequest request) {
        String requestId = extractRequestId(request);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                ex.getStatus(),
                ex.getMessage());
        detail.setProperty("code", ex.getCode());
        detail.setProperty("requestId", requestId);
        detail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(ex.getStatus()).body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {
        String requestId = extractRequestId(request);
        List<Object> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(error.getField(), error.getDefaultMessage()))
                .map(e -> (Object) e)
                .toList();

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(400),
                "Validation failed");
        detail.setProperty("code", "VALIDATION_ERROR");
        detail.setProperty("requestId", requestId);
        detail.setProperty("timestamp", Instant.now());
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    private String extractRequestId(WebRequest request) {
        String header = request.getHeader("X-Request-Id");
        return header != null ? header : UUID.randomUUID().toString();
    }

    /** DTO simples para erros de validacao. */
    public record ErrorDetail(String field, String message) {}
}
