package ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import ecommerce_event_driven.user.modules.address.application.dtos.UpdateAddressRequest;

public interface UpdateAddressPort {
    AddressResponse updateAddress(Long idUser, Long idAddress, UpdateAddressRequest request, boolean isPatch);
}
