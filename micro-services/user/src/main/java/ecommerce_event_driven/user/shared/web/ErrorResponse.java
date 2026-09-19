package ecommerce_event_driven.user.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String requestId,
        String timestamp,
        List<FieldError> errors) {

    public record FieldError(String field, String message) {}
}
