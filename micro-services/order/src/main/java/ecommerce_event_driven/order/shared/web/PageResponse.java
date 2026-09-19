package ecommerce_event_driven.order.shared.web;

import java.util.List;

/**
 * Resposta paginada padrao para listas.
 */
public record PageResponse<T>(
        List<T> content,
        PageMeta page
) {
    public record PageMeta(
            Integer number,
            Integer size,
            Long totalElements,
            Integer totalPages
    ) {}
}
