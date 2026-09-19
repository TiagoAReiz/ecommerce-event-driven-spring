package ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import ecommerce_event_driven.user.modules.address.application.dtos.CreateAddressRequest;

public interface CreateAddressPort {
    AddressResponse createAddress(Long idUser, CreateAddressRequest request);
}
