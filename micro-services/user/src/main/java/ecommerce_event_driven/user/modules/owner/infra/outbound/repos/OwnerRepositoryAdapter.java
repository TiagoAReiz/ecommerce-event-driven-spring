package ecommerce_event_driven.user.modules.owner.infra.outbound.repos;

import ecommerce_event_driven.user.modules.owner.application.mappers.OwnerMapper;
import ecommerce_event_driven.user.modules.owner.application.ports.outbound.repos.OwnerRepositoryPort;
import ecommerce_event_driven.user.modules.owner.domain.models.Owner;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OwnerRepositoryAdapter implements OwnerRepositoryPort {

    private final OwnerJpaRepository jpaRepository;

    public OwnerRepositoryAdapter(OwnerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Flush imediato de proposito. No flush o Hibernate executa todo INSERT antes
     * de qualquer UPDATE: na troca de dono, o INSERT do novo chegaria ao banco
     * antes do UPDATE que desativa o antigo, e owner_single_uk recusaria. Owner
     * e escrito rarissimamente, o flush a mais nao custa nada.
     */
    @Override
    public Owner save(Owner owner) {
        return OwnerMapper.toDomain(jpaRepository.saveAndFlush(OwnerMapper.toEntity(owner)));
    }

    @Override
    public Optional<Owner> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(OwnerMapper::toDomain);
    }

    @Override
    public Optional<Owner> findByIdUser(Long idUser) {
        return jpaRepository.findByIdUserAndDeletedAtIsNull(idUser).map(OwnerMapper::toDomain);
    }

    @Override
    public Optional<Owner> findActive() {
        return jpaRepository.findByDeletedAtIsNull().map(OwnerMapper::toDomain);
    }
}
