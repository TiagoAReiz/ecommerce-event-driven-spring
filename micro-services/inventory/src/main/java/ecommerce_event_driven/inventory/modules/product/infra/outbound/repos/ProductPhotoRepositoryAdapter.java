package ecommerce_event_driven.inventory.modules.product.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.application.mappers.ProductPhotoMapper;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.ProductPhotoRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.models.ProductPhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProductPhotoRepositoryAdapter implements ProductPhotoRepositoryPort {

    private final ProductPhotoJpaRepository jpaRepository;

    public ProductPhotoRepositoryAdapter(ProductPhotoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ProductPhoto save(ProductPhoto photo) {
        return ProductPhotoMapper.toDomain(
                jpaRepository.save(ProductPhotoMapper.toEntity(photo)));
    }

    @Override
    public Optional<ProductPhoto> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(ProductPhotoMapper::toDomain);
    }

    @Override
    public List<ProductPhoto> findByIdProduct(Long idProduct) {
        return jpaRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(idProduct).stream()
                .map(ProductPhotoMapper::toDomain)
                .toList();
    }
}
