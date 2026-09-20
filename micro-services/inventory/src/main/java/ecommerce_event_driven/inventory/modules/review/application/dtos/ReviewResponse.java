package ecommerce_event_driven.inventory.modules.review.application.dtos;

import java.time.Instant;
import java.util.Objects;

public record ReviewResponse(
        Long id,
        Integer rate,
        String title,
        String description,
        AuthorSnapshot author,
        Instant createdAt,
        Instant editedAt) {

    public record AuthorSnapshot(Long id, String name, String photoUrl) {}

    public ReviewResponse withEditedAt(Instant editedAt) {
        if (Objects.equals(this.editedAt, editedAt)) {
            return this;
        }
        return new ReviewResponse(id, rate, title, description, author, createdAt, editedAt);
    }
}
