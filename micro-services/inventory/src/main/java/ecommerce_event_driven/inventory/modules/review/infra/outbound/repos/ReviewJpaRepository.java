package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, Long> {

    Page<ReviewEntity> findByIdProductAndDeletedAtIsNull(Long idProduct, Pageable pageable);

    /** Casa com review_user_product_order_uk: uma review por compra. */
    boolean existsByIdUserAndIdProductAndIdOrderAndDeletedAtIsNull(
            Long idUser, Long idProduct, Long idOrder);

    Optional<ReviewEntity> findByIdAndDeletedAtIsNull(Long id);
}
