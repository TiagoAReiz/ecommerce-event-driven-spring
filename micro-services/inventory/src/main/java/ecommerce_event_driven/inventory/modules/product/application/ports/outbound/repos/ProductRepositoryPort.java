package ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.domain.models.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 *
 * <p>As listagens sao paginadas porque nao tem teto natural.
 */
public interface ProductRepositoryPort {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Page<Product> findByIdCategory(Long idCategory, Pageable pageable);

    long countActiveByCategory(Long idCategory);
}
