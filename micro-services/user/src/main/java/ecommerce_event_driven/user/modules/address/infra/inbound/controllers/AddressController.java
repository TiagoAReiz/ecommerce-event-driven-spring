package ecommerce_event_driven.user.modules.address.infra.inbound.controllers;

import ecommerce_event_driven.user.modules.address.application.dtos.AddressResponse;
import ecommerce_event_driven.user.modules.address.application.dtos.CreateAddressRequest;
import ecommerce_event_driven.user.modules.address.application.dtos.UpdateAddressRequest;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.CreateAddressPort;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.DeleteAddressPort;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.GetMyAddressPort;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.ListMyAddressesPort;
import ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases.UpdateAddressPort;
import ecommerce_event_driven.user.shared.security.CurrentUser;
import ecommerce_event_driven.user.shared.web.BadRequestException;
import ecommerce_event_driven.user.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/users/me/addresses")
public class AddressController {

    private final CurrentUser currentUser;
    private final ListMyAddressesPort listMyAddresses;
    private final GetMyAddressPort getMyAddress;
    private final CreateAddressPort createAddress;
    private final UpdateAddressPort updateAddress;
    private final DeleteAddressPort deleteAddress;

    public AddressController(
            CurrentUser currentUser,
            ListMyAddressesPort listMyAddresses,
            GetMyAddressPort getMyAddress,
            CreateAddressPort createAddress,
            UpdateAddressPort updateAddress,
            DeleteAddressPort deleteAddress) {
        this.currentUser = currentUser;
        this.listMyAddresses = listMyAddresses;
        this.getMyAddress = getMyAddress;
        this.createAddress = createAddress;
        this.updateAddress = updateAddress;
        this.deleteAddress = deleteAddress;
    }

    @GetMapping
    public PageResponse<AddressResponse> listAddresses(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }

        if (size > 100) {
            throw new BadRequestException("size deve ser no maximo 100");
        }

        Sort.Order order = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, Sort.by(order));

        return listMyAddresses.listMyAddresses(idUser, pageable);
    }

    @GetMapping("/{id}")
    public AddressResponse getAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }

        return getMyAddress.getMyAddress(idUser, id)
                .orElseThrow(() -> new ecommerce_event_driven.user.shared.web.NotFoundException("endereco nao encontrado"));
    }

    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateAddressRequest request) {

        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }

        AddressResponse created = createAddress.createAddress(idUser, request);

        String location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUriString();

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", location)
                .body(created);
    }

    @PutMapping("/{id}")
    public AddressResponse updateAddressPut(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody UpdateAddressRequest request) {

        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }

        return updateAddress.updateAddress(idUser, id, request, false);
    }

    @PatchMapping("/{id}")
    public AddressResponse updateAddressPatch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody UpdateAddressRequest request) {

        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }

        if (request.name() == null && request.zipcode() == null && request.country() == null
                && request.state() == null && request.city() == null && request.street() == null
                && request.number() == null) {
            throw new BadRequestException("corpo nao pode estar vazio");
        }

        return updateAddress.updateAddress(idUser, id, request, true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }

        deleteAddress.deleteAddress(idUser, id);
        return ResponseEntity.noContent().build();
    }

    private Sort.Order parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return new Sort.Order(Sort.Direction.DESC, "createdAt");
        }

        String[] parts = sortParam.split(",");
        String field = parts[0];
        String direction = parts.length > 1 ? parts[1] : "asc";

        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return new Sort.Order(dir, field);
    }
}
