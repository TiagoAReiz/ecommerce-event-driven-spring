package ecommerce_event_driven.inventory.modules.review.infra.inbound.controllers;

import ecommerce_event_driven.inventory.modules.review.application.dtos.CreateReviewRequest;
import ecommerce_event_driven.inventory.modules.review.application.dtos.PendingReviewResponse;
import ecommerce_event_driven.inventory.modules.review.application.dtos.ReviewResponse;
import ecommerce_event_driven.inventory.modules.review.application.dtos.ReviewsWithSummaryResponse;
import ecommerce_event_driven.inventory.modules.review.application.dtos.UpdateReviewRequest;
import ecommerce_event_driven.inventory.modules.review.application.usecases.ReviewService;
import ecommerce_event_driven.inventory.shared.security.CurrentUser;
import ecommerce_event_driven.inventory.shared.web.ForbiddenException;
import ecommerce_event_driven.inventory.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/products/{id}/reviews")
    public ReviewsWithSummaryResponse listProductReviews(
            @PathVariable Long id,
            @RequestParam(required = false) Integer rate,
            @PageableDefault(size = 20) Pageable pageable) {
        return reviews.listByProduct(id, rate, pageable);
    }

    // Declarado antes de /reviews/{id}: "mine" e "pending" nao sao ids.
    @GetMapping("/reviews/mine")
    public PageResponse<ReviewResponse> listMine(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        return reviews.listMine(requireUser(jwt), pageable);
    }

    @GetMapping("/reviews/pending")
    public PageResponse<PendingReviewResponse> listPending(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        return reviews.listPending(requireUser(jwt), pageable);
    }

    @GetMapping("/reviews/{id}")
    public ReviewResponse getReview(@PathVariable Long id) {
        return reviews.getById(id);
    }

    @PostMapping("/products/{id}/reviews")
    public ResponseEntity<ReviewResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse created = reviews.create(requireUser(jwt), id, request);
        return ResponseEntity.created(URI.create("/reviews/" + created.id())).body(created);
    }

    @PatchMapping("/reviews/{id}")
    public ReviewResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewRequest request) {
        return reviews.update(requireUser(jwt), id, request);
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        reviews.delete(requireUser(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Token de anonimo ou de servico nao tem dono: nao ha "minhas avaliacoes" para eles. */
    private Long requireUser(Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        if (user == null || user.id() == null) {
            throw new ForbiddenException("rota exige um usuario autenticado");
        }
        return user.id();
    }
}
