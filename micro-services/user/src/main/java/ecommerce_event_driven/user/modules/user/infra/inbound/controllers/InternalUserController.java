package ecommerce_event_driven.user.modules.user.infra.inbound.controllers;

import ecommerce_event_driven.user.modules.address.application.dtos.InternalAddressResponse;
import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.IsStoreOwnerPort;
import ecommerce_event_driven.user.modules.user.application.dtos.InternalUserProfileResponse;
import ecommerce_event_driven.user.modules.user.application.dtos.InternalUserResponse;
import ecommerce_event_driven.user.modules.user.application.dtos.InternalUsersListResponse;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import ecommerce_event_driven.user.shared.web.BadRequestException;
import ecommerce_event_driven.user.shared.web.NotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas internas servidor-a-servidor. Nao sao expostas na borda do gateway.
 * Escopo exigido: internal:hydrate (token de servico).
 */
@RestController
@RequestMapping("/internal")
public class InternalUserController {

    private final UserRepositoryPort userRepository;
    private final AddressRepositoryPort addressRepository;
    private final IsStoreOwnerPort isStoreOwner;

    public InternalUserController(
            UserRepositoryPort userRepository,
            AddressRepositoryPort addressRepository,
            IsStoreOwnerPort isStoreOwner) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.isStoreOwner = isStoreOwner;
    }

    @GetMapping("/users")
    public InternalUsersListResponse getUsersInBatch(@RequestParam String ids) {
        if (ids == null || ids.isBlank()) {
            throw new BadRequestException("ids parametro e obrigatorio");
        }

        String[] idArray = ids.split(",");
        if (idArray.length > 100) {
            throw new BadRequestException("maximo 100 ids permitidos");
        }

        List<Long> requestedIds = new ArrayList<>();
        try {
            for (String id : idArray) {
                requestedIds.add(Long.parseLong(id.trim()));
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("todos os ids devem ser numeros");
        }

        List<InternalUserResponse> users = new ArrayList<>();
        Set<Long> foundIds = new HashSet<>();

        for (Long id : requestedIds) {
            userRepository.findById(id).ifPresent(user -> {
                users.add(InternalUserResponse.from(user));
                foundIds.add(id);
            });
        }

        List<Long> missing = new ArrayList<>();
        for (Long id : requestedIds) {
            if (!foundIds.contains(id)) {
                missing.add(id);
            }
        }

        return new InternalUsersListResponse(users, missing);
    }

    @GetMapping("/users/{id}/profile")
    public InternalUserProfileResponse getUserProfile(@PathVariable Long id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("usuario nao encontrado"));

        boolean isOwner = isStoreOwner.isStoreOwner(id);
        List<String> roles = isOwner ? List.of("customer", "owner") : List.of("customer");

        return InternalUserProfileResponse.from(user, roles);
    }

    @GetMapping("/addresses/{id}")
    public InternalAddressResponse getAddress(@PathVariable Long id, @RequestParam Long userId) {
        if (userId == null) {
            throw new BadRequestException("userId parametro e obrigatorio");
        }

        var address = addressRepository.findByIdIncludingDeleted(id)
                .orElseThrow(() -> new NotFoundException("endereco nao encontrado"));

        if (!address.idUser().equals(userId)) {
            throw new NotFoundException("endereco nao pertence ao usuario");
        }

        return InternalAddressResponse.from(address);
    }
}
