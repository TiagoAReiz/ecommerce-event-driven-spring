package ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import ecommerce_event_driven.user.shared.web.PageMeta;
import ecommerce_event_driven.user.shared.web.PageResponse;
import org.springframework.data.domain.Pageable;

public interface ListMyAddressesPort {
    PageResponse<AddressResponse> listMyAddresses(Long idUser, Pageable pageable);
}
