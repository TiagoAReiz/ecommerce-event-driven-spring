package ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.domain.models.ProductPhoto;
import java.util.List;
import java.util.Optional;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 */
public interface ProductPhotoRepositoryPort {

    ProductPhoto save(ProductPhoto photo);

    Optional<ProductPhoto> findById(Long id);

    /** Ja ordenado por position: e a ordem de exibicao. */
    List<ProductPhoto> findByIdProduct(Long idProduct);
}
