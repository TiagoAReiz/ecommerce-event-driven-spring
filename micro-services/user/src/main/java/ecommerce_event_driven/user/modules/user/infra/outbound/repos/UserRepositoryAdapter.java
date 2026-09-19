package ecommerce_event_driven.user.modules.user.infra.outbound.repos;

import ecommerce_event_driven.user.modules.user.application.mappers.UserMapper;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * O retorno traz created_at/updated_at ja preenchidos: sao colunas
     * anotadas com Generated, entao o Hibernate as rele do banco depois
     * do write.
     */
    @Override
    public User save(User user) {
        return UserMapper.toDomain(jpaRepository.save(UserMapper.toEntity(user)));
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findActiveByEmail(email).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByGoogleSub(String googleSub) {
        return jpaRepository.findByGoogleSubAndDeletedAtIsNull(googleSub).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByCpf(String cpf) {
        return jpaRepository.findByCpfAndDeletedAtIsNull(cpf).map(UserMapper::toDomain);
    }
}
