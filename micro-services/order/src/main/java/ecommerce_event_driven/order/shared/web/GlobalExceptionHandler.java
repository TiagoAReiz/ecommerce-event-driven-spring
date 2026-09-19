package ecommerce_event_driven.order.shared.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Mapeia excecoes de negocio para ProblemDetail (RFC 9457).
 * Nunca vaza stack trace nem SQL.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetailResponse> handleApiException(
            ApiException ex,
            WebRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        var response = new ProblemDetailResponse(
                "https://api.loja.dev/errors/" + ex.getCode(),
                ex.getCode(),
                ex.getStatus().value(),
                ex.getMessage(),
                requestId,
                Instant.now(),
                null
        );
        return new ResponseEntity<>(response, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        List<FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.add(new FieldError(error.getField(), error.getDefaultMessage()))
        );

        var response = new ProblemDetailResponse(
                "https://api.loja.dev/errors/VALIDATION_FAILED",
                "VALIDATION_FAILED",
                HttpStatus.BAD_REQUEST.value(),
                "Validacao falhou",
                requestId,
                Instant.now(),
                errors
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    public record ProblemDetailResponse(
            String type,
            String code,
            Integer status,
            String detail,
            String requestId,
            Instant timestamp,
            List<FieldError> errors
    ) {}

    public record FieldError(
            String field,
            String message
    ) {}
}
