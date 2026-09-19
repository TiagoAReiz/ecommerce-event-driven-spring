package ecommerce_event_driven.payment.shared.web;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        PageMeta page
) {
}

record PageMeta(
        int number,
        int size,
        long totalElements,
        int totalPages
) {
}
