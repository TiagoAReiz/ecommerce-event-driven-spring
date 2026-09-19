package ecommerce_event_driven.payment.shared.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(ex.getHttpStatus());
        problem.setProperty("code", ex.getCode());
        problem.setProperty("requestId", request.getHeader("X-Request-Id"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(ex.getHttpStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(400);
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("requestId", request.getHeader("X-Request-Id"));
        problem.setProperty("timestamp", Instant.now());

        List<Object> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            var errorObj = new Object() {
                public String field = error.getField();
                public String message = error.getDefaultMessage();
            };
            errors.add(errorObj);
        });
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }
}
