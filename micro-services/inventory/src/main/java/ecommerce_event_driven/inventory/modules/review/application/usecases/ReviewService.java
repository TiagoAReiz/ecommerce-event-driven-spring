package ecommerce_event_driven.inventory.modules.review.application.usecases;

import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductPhotoJpaRepository;
import ecommerce_event_driven.inventory.modules.review.application.dtos.CreateReviewRequest;
import ecommerce_event_driven.inventory.modules.review.application.dtos.PendingReviewResponse;
import ecommerce_event_driven.inventory.modules.review.application.dtos.ReviewResponse;
import ecommerce_event_driven.inventory.modules.review.application.dtos.ReviewsWithSummaryResponse;
import ecommerce_event_driven.inventory.modules.review.application.dtos.UpdateReviewRequest;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.ReviewEligibilityJpaRepository;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.ReviewJpaRepository;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEntity;
import ecommerce_event_driven.inventory.shared.client.UserServiceClient;
import ecommerce_event_driven.inventory.shared.web.ConflictException;
import ecommerce_event_driven.inventory.shared.web.ForbiddenException;
import ecommerce_event_driven.inventory.shared.web.NotFoundException;
import ecommerce_event_driven.inventory.shared.web.PageMeta;
import ecommerce_event_driven.inventory.shared.web.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avaliacoes de produto.
 *
 * <p>So avalia quem comprou e recebeu: a prova esta em review_eligibility, preenchida pelo
 * evento order.delivered. Nenhuma chamada ao order acontece aqui.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** Depois disso a avaliacao vira historico: quem leu ja decidiu a compra. */
    private static final Duration EDIT_WINDOW = Duration.ofDays(30);

    private final ReviewJpaRepository reviews;
    private final ReviewEligibilityJpaRepository eligibilities;
    private final ProductJpaRepository products;
    private final ProductPhotoJpaRepository photos;
    private final UserServiceClient users;

    public ReviewService(
            ReviewJpaRepository reviews,
            ReviewEligibilityJpaRepository eligibilities,
            ProductJpaRepository products,
            ProductPhotoJpaRepository photos,
            UserServiceClient users) {
        this.reviews = reviews;
        this.eligibilities = eligibilities;
        this.products = products;
        this.photos = photos;
        this.users = users;
    }

    public ReviewsWithSummaryResponse listByProduct(Long idProduct, Integer rate, Pageable pageable) {
        requireActiveProduct(idProduct);

        Page<ReviewEntity> page = rate == null
                ? reviews.findByIdProductAndDeletedAtIsNullOrderByCreatedAtDesc(idProduct, pageable)
                : reviews.findByIdProductAndRateAndDeletedAtIsNullOrderByCreatedAtDesc(idProduct, rate.shortValue(), pageable);

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int nota = 5; nota >= 1; nota--) {
            distribution.put(nota, reviews.countByProductAndRate(idProduct, (short) nota));
        }

        var summary = new ReviewsWithSummaryResponse.SummaryResponse(
                averageOf(idProduct).toPlainString(),
                reviews.countByProductNotDeleted(idProduct).intValue(),
                distribution);

        return new ReviewsWithSummaryResponse(
                page.getContent().stream().map(ReviewService::toResponse).toList(),
                new ecommerce_event_driven.inventory.modules.review.application.dtos.PageResponse(
                        page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()),
                summary);
    }

    public ReviewResponse getById(Long id) {
        return toResponse(requireReview(id));
    }

    public PageResponse<ReviewResponse> listMine(Long idUser, Pageable pageable) {
        Page<ReviewEntity> page = reviews.findByIdUserAndDeletedAtIsNullOrderByCreatedAtDesc(idUser, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(ReviewService::toResponse).toList(),
                meta(page));
    }

    /** Elegibilidades ainda sem avaliacao: e o "avalie sua compra" do cliente. */
    public PageResponse<PendingReviewResponse> listPending(Long idUser, Pageable pageable) {
        var page = eligibilities.findPendingByUser(idUser, pageable);

        List<PendingReviewResponse> content = page.getContent().stream()
                .map(eligibility -> {
                    Long idProduct = eligibility.getId().getIdProduct();
                    String name = products.findById(idProduct)
                            .map(product -> product.getName())
                            .orElse(null);
                    String photoUrl = photos.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(idProduct).stream()
                            .findFirst()
                            .map(photo -> photo.getPhotoUrl())
                            .orElse(null);
                    return new PendingReviewResponse(
                            idProduct, name, photoUrl, eligibility.getId().getIdOrder(), eligibility.getGrantedAt());
                })
                .toList();

        return new PageResponse<>(content, meta(page));
    }

    @Transactional
    public ReviewResponse create(Long idUser, Long idProduct, CreateReviewRequest request) {
        requireActiveProduct(idProduct);

        if (eligibilities.findByIdUserAndIdProductAndIdOrder(idUser, idProduct, request.idOrder()).isEmpty()) {
            // Produto existe e e publico; o que falta e direito de escrever.
            throw new ForbiddenException("sem elegibilidade: este pedido nao foi entregue para este produto");
        }
        if (reviews.existsByIdUserAndIdProductAndIdOrderAndDeletedAtIsNull(idUser, idProduct, request.idOrder())) {
            throw new ConflictException("este produto ja foi avaliado neste pedido");
        }

        // Snapshot do autor: o nome na avaliacao e o da epoca, mesmo que o usuario mude depois.
        String name = null;
        String photoUrl = null;
        try {
            var batch = users.getUsersById(List.of(idUser));
            if (batch != null && batch.users() != null && !batch.users().isEmpty()) {
                name = batch.users().get(0).name();
                photoUrl = batch.users().get(0).photoUrl();
            }
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel buscar o autor {} para o snapshot: {}", idUser, e.getMessage());
        }

        ReviewEntity saved = reviews.save(ReviewEntity.builder()
                .idUser(idUser)
                .userName(name)
                .userPhotoUrl(photoUrl)
                .idProduct(idProduct)
                .idOrder(request.idOrder())
                .rate(request.rate().shortValue())
                .title(request.title())
                .description(request.description())
                .build());

        recalculateRating(idProduct);
        return toResponse(saved);
    }

    @Transactional
    public ReviewResponse update(Long idUser, Long id, UpdateReviewRequest request) {
        ReviewEntity review = requireReview(id);
        requireAuthor(review, idUser);

        if (request.rate() == null && request.title() == null && request.description() == null) {
            throw new ecommerce_event_driven.inventory.shared.web.BadRequestException("corpo vazio");
        }
        if (review.getCreatedAt() != null
                && Instant.now().isAfter(review.getCreatedAt().plus(EDIT_WINDOW))) {
            throw new ConflictException("janela de edicao de 30 dias expirada");
        }

        if (request.rate() != null) {
            review.setRate(request.rate().shortValue());
        }
        if (request.title() != null) {
            review.setTitle(request.title());
        }
        if (request.description() != null) {
            review.setDescription(request.description());
        }
        ReviewEntity saved = reviews.save(review);

        recalculateRating(review.getIdProduct());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long idUser, Long id) {
        ReviewEntity review = requireReview(id);
        requireAuthor(review, idUser);

        reviews.softDeleteById(id);
        recalculateRating(review.getIdProduct());
    }

    /** Media e contagem ficam no produto para a vitrine nao precisar somar avaliacao por card. */
    private void recalculateRating(Long idProduct) {
        BigDecimal average = averageOf(idProduct);
        int count = reviews.countByProductNotDeleted(idProduct).intValue();
        products.updateRating(idProduct, average, count);
    }

    private BigDecimal averageOf(Long idProduct) {
        Double average = reviews.averageRatingByProduct(idProduct);
        return BigDecimal.valueOf(average == null ? 0.0 : average).setScale(2, RoundingMode.HALF_UP);
    }

    private ReviewEntity requireReview(Long id) {
        return reviews.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("avaliacao nao encontrada"));
    }

    /** Avaliacao de outro autor responde 404: nao confirma que aquele id existe. */
    private void requireAuthor(ReviewEntity review, Long idUser) {
        if (!review.getIdUser().equals(idUser)) {
            throw new NotFoundException("avaliacao nao encontrada");
        }
    }

    private void requireActiveProduct(Long idProduct) {
        products.findById(idProduct)
                .filter(product -> product.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("produto nao encontrado"));
    }

    private static PageMeta meta(Page<?> page) {
        return new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static ReviewResponse toResponse(ReviewEntity review) {
        Instant editedAt = review.getUpdatedAt() != null && !review.getUpdatedAt().equals(review.getCreatedAt())
                ? review.getUpdatedAt()
                : null;
        return new ReviewResponse(
                review.getId(),
                review.getRate() == null ? null : review.getRate().intValue(),
                review.getTitle(),
                review.getDescription(),
                new ReviewResponse.AuthorSnapshot(review.getIdUser(), review.getUserName(), review.getUserPhotoUrl()),
                review.getCreatedAt(),
                editedAt);
    }
}
