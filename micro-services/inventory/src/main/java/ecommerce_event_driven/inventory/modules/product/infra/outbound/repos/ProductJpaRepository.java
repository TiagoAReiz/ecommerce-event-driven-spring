package ecommerce_event_driven.inventory.modules.product.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

    /** Vitrine do vendedor. Paginado: a lista nao tem teto natural. */
    Page<ProductEntity> findByCategoryIdAndDeletedAtIsNull(Long idCategory, Pageable pageable);

    /**
     * SELECT ... FOR UPDATE na linha do produto.
     *
     * <p>E ele que serializa as reservas concorrentes do mesmo produto. Sem o
     * lock, duas transacoes leriam a mesma disponibilidade, as duas passariam
     * no teste e as duas inseririam reserva: o READ COMMITTED do Postgres nao
     * enxerga a linha que a outra ainda nao commitou.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.id = :idProduct and p.deletedAt is null")
    Optional<ProductEntity> findByIdForUpdate(@Param("idProduct") Long idProduct);

    @Query("select count(p) from ProductEntity p where p.category.id = :idCategory and p.deletedAt is null")
    long countActiveByCategory(@Param("idCategory") Long idCategory);
}
