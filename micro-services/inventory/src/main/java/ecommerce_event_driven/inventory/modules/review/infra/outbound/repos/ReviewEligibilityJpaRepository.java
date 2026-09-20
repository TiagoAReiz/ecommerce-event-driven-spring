package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos;

import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityEntity;
import ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity.ReviewEligibilityId;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewEligibilityJpaRepository extends JpaRepository<ReviewEligibilityEntity, ReviewEligibilityId> {

    /**
     * Encontra elegibilidade por usuario, produto e pedido.
     */
    @Query("""
            select re from ReviewEligibilityEntity re
             where re.id.idUser = :idUser
               and re.id.idProduct = :idProduct
               and re.id.idOrder = :idOrder
            """)
    Optional<ReviewEligibilityEntity> findByIdUserAndIdProductAndIdOrder(
            @Param("idUser") Long idUser,
            @Param("idProduct") Long idProduct,
            @Param("idOrder") Long idOrder);

    /**
     * Lista elegibilidades sem reviews correspondentes para um usuario.
     */
    @Query("""
            select re from ReviewEligibilityEntity re
             where re.id.idUser = :idUser
               and not exists (
                   select 1 from ReviewEntity r
                    where r.idUser = re.id.idUser
                      and r.idProduct = re.id.idProduct
                      and r.idOrder = re.id.idOrder
                      and r.deletedAt is null
               )
             order by re.grantedAt desc
            """)
    Page<ReviewEligibilityEntity> findPendingByUser(@Param("idUser") Long idUser, Pageable pageable);
}
