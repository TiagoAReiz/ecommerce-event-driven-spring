package ecommerce_event_driven.user.modules.address.application.ports.outbound.repos;

import ecommerce_event_driven.user.modules.address.domain.models.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Leituras enxergam apenas registros ativos. Remocao e logica: salve o modelo
 * com deletedAt preenchido.
 */
public interface AddressRepositoryPort {

    Address save(Address address);

    Optional<Address> findById(Long id);

    /** Busca escopada no dono, para nao vazar endereco de outro usuario. */
    Optional<Address> findByIdAndIdUser(Long id, Long idUser);

    /** Rota interna: busca endereco inclusive removido (para validar historico). */
    Optional<Address> findByIdIncludingDeleted(Long id);

    List<Address> findByIdUser(Long idUser);

    /** Listagem paginada de enderecos ativos do usuario. */
    Page<Address> findByIdUserPaginated(Long idUser, Pageable pageable);
}
