package ecommerce_event_driven.inventory.modules.product.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductPhotoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductPhotoJpaRepository extends JpaRepository<ProductPhotoEntity, Long> {

    List<ProductPhotoEntity> findByProductIdAndDeletedAtIsNullOrderByPositionAsc(Long idProduct);

    /** Fotos de varios produtos de uma vez, para a vitrine nao consultar uma por item. */
    List<ProductPhotoEntity> findByProductIdInAndDeletedAtIsNullOrderByProductIdAscPositionAsc(List<Long> idProducts);

    Optional<ProductPhotoEntity> findByIdAndDeletedAtIsNull(Long id);
}
