package ecommerce_event_driven.inventory.shared.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.FieldError;

/**
 * Manipulador global de excecoes da API. Retorna ProblemDetail
 * (application/problem+json) com propriedades extras code, requestId, timestamp
 * e errors[] para validacao.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(
            ApiException ex,
            WebRequest request) {

        ProblemDetail detail = ProblemDetail.forStatus(ex.getHttpStatus());
        detail.setTitle(ex.getHttpStatus().getReasonPhrase());
        detail.setDetail(ex.getMessage());
        detail.setProperty("code", ex.getCode());
        detail.setProperty("requestId", getRequestId(request));
        detail.setProperty("timestamp", Instant.now());

        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        List<Object> errors = new ArrayList<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.add(new ErrorDetail(fieldName, errorMessage));
        });

        ProblemDetail detail = ProblemDetail.forStatus(400);
        detail.setTitle("Validation Failed");
        detail.setDetail("One or more validation errors occurred");
        detail.setProperty("code", "VALIDATION_ERROR");
        detail.setProperty("requestId", getRequestId(request));
        detail.setProperty("timestamp", Instant.now());
        detail.setProperty("errors", errors);

        return ResponseEntity
                .badRequest()
                .body(detail);
    }

    private String getRequestId(WebRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }

    public record ErrorDetail(String field, String message) {
    }
}
