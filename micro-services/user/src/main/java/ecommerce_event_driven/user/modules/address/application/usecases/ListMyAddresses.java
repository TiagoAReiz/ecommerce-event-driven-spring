package ecommerce_event_driven.user.modules.address.application.usecases;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.ListMyAddressesPort;
import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.shared.web.PageMeta;
import ecommerce_event_driven.user.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ListMyAddresses implements ListMyAddressesPort {

    private final AddressRepositoryPort addressRepository;

    public ListMyAddresses(AddressRepositoryPort addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Override
    public PageResponse<AddressResponse> listMyAddresses(Long idUser, Pageable pageable) {
        Page<AddressResponse> page = addressRepository.findByIdUserPaginated(idUser, pageable)
                .map(AddressResponse::from);

        PageMeta meta = new PageMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());

        return new PageResponse<>(page.getContent(), meta);
    }
}
