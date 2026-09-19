package ecommerce_event_driven.user.modules.address.application.usecases;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import ecommerce_event_driven.user.modules.address.application.dtos.UpdateAddressRequest;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.UpdateAddressPort;
import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.modules.address.domain.models.Address;
import ecommerce_event_driven.user.shared.web.BadRequestException;
import ecommerce_event_driven.user.shared.web.NotFoundException;
import ecommerce_event_driven.user.shared.web.UnprocessableException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateAddress implements UpdateAddressPort {

    private static final String STATE_PATTERN = "^[A-Z]{2}$";
    private static final String ZIPCODE_PATTERN = "^\\d{8}$";

    private final AddressRepositoryPort addressRepository;

    public UpdateAddress(AddressRepositoryPort addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(Long idUser, Long idAddress, UpdateAddressRequest request, boolean isPatch) {
        Address existing = addressRepository.findByIdAndIdUser(idAddress, idUser)
                .orElseThrow(() -> new NotFoundException("endereco nao encontrado"));

        // Validacoes dos campos fornecidos
        if (request.zipcode() != null && !request.zipcode().matches(ZIPCODE_PATTERN)) {
            throw new BadRequestException("CEP deve ter 8 digitos");
        }

        if (request.state() != null && (request.country() == null || "BR".equals(request.country()) || "BR".equals(existing.country()))) {
            if (!request.state().matches(STATE_PATTERN)) {
                throw new UnprocessableException("INVALID_STATE", "UF deve ter 2 letras");
            }
        }

        if (request.city() != null && request.city().isBlank()) {
            throw new BadRequestException("city nao pode ser vazio");
        }

        if (request.street() != null && request.street().isBlank()) {
            throw new BadRequestException("street nao pode ser vazio");
        }

        if (request.name() != null && request.name().length() > 60) {
            throw new BadRequestException("name deve ter no maximo 60 caracteres");
        }

        // Para PUT, campos ausentes viram null; para PATCH, herdam do anterior
        Address updated = Address.builder()
                .idUser(idUser)
                .name(isPatch && request.name() == null ? existing.name() : request.name())
                .zipcode(isPatch && request.zipcode() == null ? existing.zipcode() : request.zipcode())
                .country(isPatch && request.country() == null ? existing.country() : request.country())
                .state(isPatch && request.state() == null ? existing.state() : request.state())
                .city(isPatch && request.city() == null ? existing.city() : request.city())
                .street(isPatch && request.street() == null ? existing.street() : request.street())
                .number(isPatch && request.number() == null ? existing.number() : request.number())
                .build();

        Address saved = addressRepository.save(updated);

        // Soft delete do endereco antigo
        Address deletedOld = existing.toBuilder().deletedAt(Instant.now()).build();
        addressRepository.save(deletedOld);

        return AddressResponse.from(saved);
    }
}
