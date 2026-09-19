package ecommerce_event_driven.inventory.modules.product.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.application.mappers.CategoryMapper;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.CategoryRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.models.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final CategoryJpaRepository jpaRepository;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Category save(Category category) {
        return CategoryMapper.toDomain(jpaRepository.save(CategoryMapper.toEntity(category)));
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(CategoryMapper::toDomain);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return jpaRepository.findBySlugAndDeletedAtIsNull(slug).map(CategoryMapper::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return jpaRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(CategoryMapper::toDomain)
                .toList();
    }
}
