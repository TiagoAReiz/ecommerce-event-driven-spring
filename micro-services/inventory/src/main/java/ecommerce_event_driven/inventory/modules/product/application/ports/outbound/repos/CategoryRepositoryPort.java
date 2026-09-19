package ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.domain.models.Category;
import java.util.List;
import java.util.Optional;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 */
public interface CategoryRepositoryPort {

    Category save(Category category);

    Optional<Category> findById(Long id);

    Optional<Category> findBySlug(String slug);

    /** Sem paginacao de proposito: o conjunto de categorias e pequeno e fechado. */
    List<Category> findAll();
}
