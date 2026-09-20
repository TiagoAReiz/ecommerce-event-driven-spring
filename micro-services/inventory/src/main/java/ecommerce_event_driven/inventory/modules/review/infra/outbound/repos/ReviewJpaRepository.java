package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, Long> {

    /**
     * Encontra review por id.
     */
    Optional<ReviewEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Encontra review do usuario para um produto em um pedido.
     */
    Optional<ReviewEntity> findByIdUserAndIdProductAndIdOrderAndDeletedAtIsNull(
            Long idUser, Long idProduct, Long idOrder);

    /**
     * Verifica se existe review do usuario para um produto em um pedido.
     */
    boolean existsByIdUserAndIdProductAndIdOrderAndDeletedAtIsNull(
            Long idUser, Long idProduct, Long idOrder);

    /**
     * Busca todas as reviews nao deletadas de um produto.
     */
    Page<ReviewEntity> findByIdProductAndDeletedAtIsNull(Long idProduct, Pageable pageable);

    /**
     * Busca todas as reviews nao deletadas de um produto, ordenadas por criacao.
     */
    Page<ReviewEntity> findByIdProductAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long idProduct, Pageable pageable);

    /**
     * Busca reviews nao deletadas por rate.
     */
    Page<ReviewEntity> findByIdProductAndRateAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long idProduct, Integer rate, Pageable pageable);

    /**
     * Busca todas as reviews do usuario nao deletadas.
     */
    Page<ReviewEntity> findByIdUserAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long idUser, Pageable pageable);

    /**
     * Calcula media de rating para um produto.
     */
    @Query("""
            select coalesce(avg(r.rate), 0.0)
              from ReviewEntity r
             where r.idProduct = :idProduct
               and r.deletedAt is null
            """)
    Double averageRatingByProduct(@Param("idProduct") Long idProduct);

    /**
     * Conta reviews nao deletadas de um produto.
     */
    @Query("""
            select count(r)
              from ReviewEntity r
             where r.idProduct = :idProduct
               and r.deletedAt is null
            """)
    Long countByProductNotDeleted(@Param("idProduct") Long idProduct);

    /**
     * Conta reviews nao deletadas por rate.
     */
    @Query("""
            select count(r)
              from ReviewEntity r
             where r.idProduct = :idProduct
               and r.rate = :rate
               and r.deletedAt is null
            """)
    Long countByProductAndRate(@Param("idProduct") Long idProduct, @Param("rate") Integer rate);

    /**
     * Soft delete de uma review.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReviewEntity r set r.deletedAt = current_timestamp where r.id = :id and r.deletedAt is null")
    int softDeleteById(@Param("id") Long id);

    /**
     * Atualiza review e seu update timestamp.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ReviewEntity r
               set r.rate = :rate, r.title = :title, r.description = :description
             where r.id = :id
               and r.deletedAt is null
            """)
    int updateReview(
            @Param("id") Long id,
            @Param("rate") Integer rate,
            @Param("title") String title,
            @Param("description") String description);

    /**
     * Anonimiza reviews de um usuario.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ReviewEntity r
               set r.userName = 'Usuário removido', r.userPhotoUrl = null
             where r.idUser = :idUser
               and r.deletedAt is null
            """)
    int anonymizeByUser(@Param("idUser") Long idUser);
}
