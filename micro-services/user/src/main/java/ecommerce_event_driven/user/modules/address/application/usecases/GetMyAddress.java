package ecommerce_event_driven.user.modules.address.application.usecases;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.GetMyAddressPort;
import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetMyAddress implements GetMyAddressPort {

    private final AddressRepositoryPort addressRepository;

    public GetMyAddress(AddressRepositoryPort addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Override
    public Optional<AddressResponse> getMyAddress(Long idUser, Long idAddress) {
        return addressRepository.findByIdAndIdUser(idAddress, idUser)
                .map(AddressResponse::from);
    }
}
