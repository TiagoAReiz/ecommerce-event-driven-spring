package ecommerce_event_driven.user.modules.owner.infra.outbound.repos;

import ecommerce_event_driven.user.modules.owner.infra.outbound.repos.entity.OwnerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OwnerJpaRepository extends JpaRepository<OwnerEntity, Long> {
    Optional<OwnerEntity> findByIdUserAndDeletedAtIsNull(Long idUser);

    Optional<OwnerEntity> findByIdAndDeletedAtIsNull(Long id);

    Optional<OwnerEntity> findByDeletedAtIsNull();
}
