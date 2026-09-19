package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.review.application.mappers.ReviewMapper;
import ecommerce_event_driven.inventory.modules.review.application.ports.outbound.repos.ReviewRepositoryPort;
import ecommerce_event_driven.inventory.modules.review.domain.models.Review;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class ReviewRepositoryAdapter implements ReviewRepositoryPort {

    private final ReviewJpaRepository jpaRepository;

    public ReviewRepositoryAdapter(ReviewJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Review save(Review review) {
        return ReviewMapper.toDomain(jpaRepository.save(ReviewMapper.toEntity(review)));
    }

    @Override
    public Optional<Review> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(ReviewMapper::toDomain);
    }

    @Override
    public Page<Review> findByIdProduct(Long idProduct, Pageable pageable) {
        return jpaRepository.findByIdProductAndDeletedAtIsNull(idProduct, pageable)
                .map(ReviewMapper::toDomain);
    }

    @Override
    public boolean existsByIdUserAndIdProductAndIdOrder(
            Long idUser, Long idProduct, Long idOrder) {
        return jpaRepository.existsByIdUserAndIdProductAndIdOrderAndDeletedAtIsNull(
                idUser, idProduct, idOrder);
    }
}
