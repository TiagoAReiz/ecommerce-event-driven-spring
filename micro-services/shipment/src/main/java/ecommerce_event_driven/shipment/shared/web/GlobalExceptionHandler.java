package ecommerce_event_driven.shipment.shared.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Handler global para excecoes da API. Responde em application/problem+json
 * com codigo estavel e sem expor stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        detail.setProperty("code", ex.getCode());
        detail.setProperty("requestId", extractRequestId(request));
        detail.setProperty("timestamp", Instant.now().toString());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validacao falhou");
        detail.setProperty("code", "BAD_REQUEST");
        detail.setProperty("requestId", extractRequestId(request));
        detail.setProperty("timestamp", Instant.now().toString());

        List<FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.add(new FieldError(err.getField(), err.getDefaultMessage())));
        detail.setProperty("errors", errors);

        return detail;
    }

    private String extractRequestId(WebRequest request) {
        String headValue = request.getHeader("X-Request-Id");
        return headValue != null ? headValue : UUID.randomUUID().toString();
    }

    public record FieldError(String field, String message) {}
}
