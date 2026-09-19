package ecommerce_event_driven.user.modules.address.infra.outbound.repos;

import ecommerce_event_driven.user.modules.address.application.mappers.AddressMapper;
import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.modules.address.domain.models.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AddressRepositoryAdapter implements AddressRepositoryPort {

    private final AddressJpaRepository jpaRepository;

    public AddressRepositoryAdapter(AddressJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Address save(Address address) {
        return AddressMapper.toDomain(jpaRepository.save(AddressMapper.toEntity(address)));
    }

    @Override
    public Optional<Address> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(AddressMapper::toDomain);
    }

    @Override
    public Optional<Address> findByIdAndIdUser(Long id, Long idUser) {
        return jpaRepository.findByIdAndIdUserAndDeletedAtIsNull(id, idUser)
                .map(AddressMapper::toDomain);
    }

    @Override
    public List<Address> findByIdUser(Long idUser) {
        return jpaRepository.findByIdUserAndDeletedAtIsNull(idUser).stream()
                .map(AddressMapper::toDomain)
                .toList();
    }
}
