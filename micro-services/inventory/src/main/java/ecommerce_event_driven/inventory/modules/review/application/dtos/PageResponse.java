package ecommerce_event_driven.inventory.modules.review.application.dtos;

public record PageResponse(
        Integer number,
        Integer size,
        Long totalElements,
        Integer totalPages) {}
