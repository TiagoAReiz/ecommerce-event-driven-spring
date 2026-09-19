package ecommerce_event_driven.user.modules.user.infra.inbound.controllers;

import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.IsStoreOwnerPort;
import ecommerce_event_driven.user.modules.user.application.dtos.CreateUserRequest;
import ecommerce_event_driven.user.modules.user.application.dtos.UserResponse;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.CreateUserPort;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.FindUserByEmailPort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
public class UserController {

    private final FindUserByEmailPort findUserByEmail;
    private final CreateUserPort createUser;
    private final IsStoreOwnerPort isStoreOwner;

    public UserController(
            FindUserByEmailPort findUserByEmail,
            CreateUserPort createUser,
            IsStoreOwnerPort isStoreOwner) {
        this.findUserByEmail = findUserByEmail;
        this.createUser = createUser;
        this.isStoreOwner = isStoreOwner;
    }

    @GetMapping
    public UserResponse getByEmail(@RequestParam String email) {
        return findUserByEmail.findByEmail(email)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "usuario nao encontrado"));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
        require(request.name(), "name");
        require(request.email(), "email");
        require(request.googleSub(), "googleSub");

        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toResponse(createUser.create(request)));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "usuario ja existe");
        }
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, isStoreOwner.isStoreOwner(user.id()));
    }

    /** As tres sao NOT NULL no banco. Falha aqui vira 400, nao 500 la embaixo. */
    private static void require(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " e obrigatorio");
        }
    }
}
