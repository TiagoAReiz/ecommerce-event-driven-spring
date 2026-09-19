package ecommerce_event_driven.user.modules.address.infra.outbound.repos;

import ecommerce_event_driven.user.modules.address.infra.outbound.repos.entity.AddressEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressJpaRepository extends JpaRepository<AddressEntity, Long> {

    List<AddressEntity> findByIdUserAndDeletedAtIsNull(Long idUser);

    Optional<AddressEntity> findByIdAndIdUserAndDeletedAtIsNull(Long id, Long idUser);

    Optional<AddressEntity> findByIdAndDeletedAtIsNull(Long id);

    /** Rota interna: busca endereco inclusive removido (para validar historico). */
    Optional<AddressEntity> findById(Long id);

    /** Listagem paginada de enderecos ativos do usuario. */
    Page<AddressEntity> findByIdUserAndDeletedAtIsNull(Long idUser, Pageable pageable);
}
