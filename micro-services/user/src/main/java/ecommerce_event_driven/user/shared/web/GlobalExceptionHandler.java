package ecommerce_event_driven.user.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento centralizado de erros. Responde em RFC 9457 (application/problem+json)
 * com propriedades extras: code (para o front ramificar), requestId (para amarra log),
 * timestamp e errors[] (para validacao).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException ex, HttpServletRequest request) {
        String requestId = getOrGenerateRequestId(request);
        ErrorResponse response = new ErrorResponse(
                null,
                ex.httpStatus().getReasonPhrase(),
                ex.httpStatus().value(),
                ex.getMessage(),
                request.getRequestURI(),
                ex.code(),
                requestId,
                Instant.now().toString(),
                null);
        return ResponseEntity.status(ex.httpStatus())
                .header("X-Request-Id", requestId)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = getOrGenerateRequestId(request);
        List<ErrorResponse.FieldError> errors = new ArrayList<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.add(new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage())));

        ErrorResponse response = new ErrorResponse(
                null,
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                "Validacao falhou",
                request.getRequestURI(),
                "BAD_REQUEST",
                requestId,
                Instant.now().toString(),
                errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("X-Request-Id", requestId)
                .body(response);
    }

    private String getOrGenerateRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return header != null ? header : UUID.randomUUID().toString();
    }
}
