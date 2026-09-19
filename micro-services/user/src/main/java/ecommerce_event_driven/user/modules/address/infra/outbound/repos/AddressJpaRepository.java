package ecommerce_event_driven.user.modules.address.infra.outbound.repos;

import ecommerce_event_driven.user.modules.address.infra.outbound.repos.entity.AddressEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressJpaRepository extends JpaRepository<AddressEntity, Long> {

    List<AddressEntity> findByIdUserAndDeletedAtIsNull(Long idUser);

    Optional<AddressEntity> findByIdAndIdUserAndDeletedAtIsNull(Long id, Long idUser);

    Optional<AddressEntity> findByIdAndDeletedAtIsNull(Long id);
}
