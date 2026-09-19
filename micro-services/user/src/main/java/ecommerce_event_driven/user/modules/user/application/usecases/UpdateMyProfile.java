package ecommerce_event_driven.user.modules.user.application.usecases;

import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.IsStoreOwnerPort;
import ecommerce_event_driven.user.modules.user.application.dtos.MeResponse;
import ecommerce_event_driven.user.modules.user.application.dtos.UpdateMeRequest;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.UpdateMyProfilePort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import ecommerce_event_driven.user.shared.web.ConflictException;
import ecommerce_event_driven.user.shared.web.NotFoundException;
import ecommerce_event_driven.user.shared.web.UnprocessableException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateMyProfile implements UpdateMyProfilePort {

    private static final String CPF_PATTERN = "\\d{11}";
    private static final String PHONE_PATTERN = "^\\+[1-9]\\d{7,14}$";

    private final UserRepositoryPort userRepository;
    private final AddressRepositoryPort addressRepository;
    private final IsStoreOwnerPort isStoreOwner;

    public UpdateMyProfile(
            UserRepositoryPort userRepository,
            AddressRepositoryPort addressRepository,
            IsStoreOwnerPort isStoreOwner) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.isStoreOwner = isStoreOwner;
    }

    @Override
    @Transactional
    public MeResponse updateMyProfile(Long idUser, UpdateMeRequest request) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new NotFoundException("usuario nao encontrado"));

        // Validacoes
        if (request.cpf() != null && !request.cpf().isBlank()) {
            validateCpf(request.cpf());
            // Checar duplicata
            Optional<User> existingCpf = userRepository.findByCpf(request.cpf());
            if (existingCpf.isPresent() && !existingCpf.get().id().equals(idUser)) {
                throw new ConflictException("DUPLICATE_CPF", "CPF ja pertence a outro usuario");
            }
        }

        if (request.phone() != null && !request.phone().isBlank()) {
            validatePhone(request.phone());
        }

        // Atualizar campos
        User updated = user.toBuilder()
                .name(request.name() != null ? request.name() : user.name())
                .cpf(request.cpf() != null ? request.cpf() : user.cpf())
                .phone(request.phone() != null ? request.phone() : user.phone())
                .photoUrl(request.photoUrl() != null ? request.photoUrl() : user.photoUrl())
                .build();

        try {
            User saved = userRepository.save(updated);
            long addressCount = addressRepository.findByIdUser(idUser).size();
            boolean isOwner = isStoreOwner.isStoreOwner(idUser);
            return MeResponse.from(saved, isOwner, addressCount);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("DUPLICATE_CPF", "CPF ja pertence a outro usuario");
        }
    }

    private void validateCpf(String cpf) {
        if (!cpf.matches(CPF_PATTERN)) {
            throw new UnprocessableException("INVALID_CPF", "CPF deve ter 11 digitos");
        }
        // Validar digitos verificadores (algoritmo basico)
        if (!isValidCpf(cpf)) {
            throw new UnprocessableException("INVALID_CPF", "CPF com digitos verificadores invalidos");
        }
    }

    private boolean isValidCpf(String cpf) {
        // Algoritmo de validacao de CPF
        int[] digits = new int[11];
        for (int i = 0; i < 11; i++) {
            digits[i] = cpf.charAt(i) - '0';
        }

        // Primeiro digito verificador
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += digits[i] * (10 - i);
        }
        int firstDigit = 11 - (sum % 11);
        if (firstDigit >= 10) {
            firstDigit = 0;
        }
        if (digits[9] != firstDigit) {
            return false;
        }

        // Segundo digito verificador
        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += digits[i] * (11 - i);
        }
        int secondDigit = 11 - (sum % 11);
        if (secondDigit >= 10) {
            secondDigit = 0;
        }
        return digits[10] == secondDigit;
    }

    private void validatePhone(String phone) {
        if (!phone.matches(PHONE_PATTERN)) {
            throw new UnprocessableException("INVALID_PHONE", "Telefone deve estar em formato E.164");
        }
    }
}
