package ecommerce_event_driven.inventory.modules.review.application.ports.outbound.repos;

import ecommerce_event_driven.inventory.modules.review.domain.models.Review;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 */
public interface ReviewRepositoryPort {

    Review save(Review review);

    Optional<Review> findById(Long id);

    Page<Review> findByIdProduct(Long idProduct, Pageable pageable);

    /** Uma avaliacao por compra: guarda contra review duplicada. */
    boolean existsByIdUserAndIdProductAndIdOrder(Long idUser, Long idProduct, Long idOrder);
}
