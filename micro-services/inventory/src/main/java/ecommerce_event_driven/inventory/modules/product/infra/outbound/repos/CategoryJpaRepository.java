package ecommerce_event_driven.inventory.modules.product.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.CategoryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {

    /** Casa com category_slug_uk. */
    Optional<CategoryEntity> findBySlugAndDeletedAtIsNull(String slug);

    List<CategoryEntity> findByDeletedAtIsNullOrderByNameAsc();

    Optional<CategoryEntity> findByIdAndDeletedAtIsNull(Long id);
}
