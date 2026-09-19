package ecommerce_event_driven.user.modules.owner.application.ports.outbound.repos;

import ecommerce_event_driven.user.modules.owner.domain.models.Owner;
import java.util.Optional;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 */
public interface OwnerRepositoryPort {

    Owner save(Owner owner);

    Optional<Owner> findById(Long id);

    Optional<Owner> findByIdUser(Long idUser);

    /** O dono da loja. O indice owner_single_uk garante que existe no maximo um. */
    Optional<Owner> findActive();
}
