package ecommerce_event_driven.inventory.modules.product.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.application.mappers.ProductMapper;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.ProductRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.models.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository jpaRepository;

    public ProductRepositoryAdapter(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Product save(Product product) {
        return ProductMapper.toDomain(jpaRepository.save(ProductMapper.toEntity(product)));
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(ProductMapper::toDomain);
    }

    @Override
    public Page<Product> findByIdCategory(Long idCategory, Pageable pageable) {
        return jpaRepository.findByCategoryIdAndDeletedAtIsNull(idCategory, pageable)
                .map(ProductMapper::toDomain);
    }
}
