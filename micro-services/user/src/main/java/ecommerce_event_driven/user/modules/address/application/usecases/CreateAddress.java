package ecommerce_event_driven.user.modules.address.application.usecases;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import ecommerce_event_driven.user.modules.address.application.dtos.CreateAddressRequest;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.CreateAddressPort;
import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.modules.address.domain.models.Address;
import ecommerce_event_driven.user.shared.web.BadRequestException;
import ecommerce_event_driven.user.shared.web.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAddress implements CreateAddressPort {

    private static final String STATE_PATTERN = "^[A-Z]{2}$";
    private static final String ZIPCODE_PATTERN = "^\\d{8}$";

    private final AddressRepositoryPort addressRepository;

    public CreateAddress(AddressRepositoryPort addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Override
    @Transactional
    public AddressResponse createAddress(Long idUser, CreateAddressRequest request) {
        // Validacoes
        if (request.zipcode() == null || !request.zipcode().matches(ZIPCODE_PATTERN)) {
            throw new BadRequestException("CEP deve ter 8 digitos");
        }

        String country = request.country() != null ? request.country() : "BR";
        if ("BR".equals(country) && (request.state() == null || !request.state().matches(STATE_PATTERN))) {
            throw new UnprocessableException("INVALID_STATE", "UF deve ter 2 letras");
        }

        if (request.city() == null || request.city().isBlank()) {
            throw new BadRequestException("city e obrigatorio");
        }

        if (request.street() == null || request.street().isBlank()) {
            throw new BadRequestException("street e obrigatorio");
        }

        if (request.name() != null && request.name().length() > 60) {
            throw new BadRequestException("name deve ter no maximo 60 caracteres");
        }

        Address address = Address.builder()
                .idUser(idUser)
                .name(request.name())
                .zipcode(request.zipcode())
                .country(country)
                .state(request.state())
                .city(request.city())
                .street(request.street())
                .number(request.number())
                .build();

        Address saved = addressRepository.save(address);
        return AddressResponse.from(saved);
    }
}
