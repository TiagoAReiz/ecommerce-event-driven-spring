package ecommerce_event_driven.user.modules.address.application.usecases;

import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.DeleteAddressPort;
import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.modules.address.domain.models.Address;
import ecommerce_event_driven.user.shared.web.NotFoundException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteAddress implements DeleteAddressPort {

    private final AddressRepositoryPort addressRepository;

    public DeleteAddress(AddressRepositoryPort addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Override
    @Transactional
    public void deleteAddress(Long idUser, Long idAddress) {
        Address address = addressRepository.findByIdAndIdUser(idAddress, idUser)
                .orElseThrow(() -> new NotFoundException("endereco nao encontrado"));

        Address deleted = address.toBuilder().deletedAt(Instant.now()).build();
        addressRepository.save(deleted);
    }
}
