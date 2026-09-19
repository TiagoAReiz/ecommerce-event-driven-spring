package ecommerce_event_driven.user.modules.user.application.ports.outbound.repos;

import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.util.Optional;

/**
 * Contrato de persistencia de usuario, em linguagem de dominio.
 *
 * <p>Todo metodo de leitura enxerga apenas registros ativos: linha com
 * deleted_at preenchido nao existe para o dominio.
 *
 * <p>Remocao e logica. Para remover, salve o modelo com deletedAt preenchido.
 */
public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSub(String googleSub);

    Optional<User> findByCpf(String cpf);
}
