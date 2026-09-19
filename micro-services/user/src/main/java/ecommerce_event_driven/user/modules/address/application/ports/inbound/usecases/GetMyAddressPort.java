package ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import java.util.Optional;

public interface GetMyAddressPort {
    Optional<AddressResponse> getMyAddress(Long idUser, Long idAddress);
}
