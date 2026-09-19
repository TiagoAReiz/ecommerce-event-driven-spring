package ecommerce_event_driven.user.modules.user.infra.inbound.controllers;

import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.IsStoreOwnerPort;
import ecommerce_event_driven.user.modules.user.application.dtos.CreateUserRequest;
import ecommerce_event_driven.user.modules.user.application.dtos.MeResponse;
import ecommerce_event_driven.user.modules.user.application.dtos.PublicUserResponse;
import ecommerce_event_driven.user.modules.user.application.dtos.UpdateMeRequest;
import ecommerce_event_driven.user.modules.user.application.dtos.UserResponse;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.CreateUserPort;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.DeleteMyAccountPort;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.FindUserByEmailPort;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.GetMyProfilePort;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.GetPublicProfilePort;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.UpdateMyProfilePort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import ecommerce_event_driven.user.shared.security.CurrentUser;
import ecommerce_event_driven.user.shared.web.BadRequestException;
import ecommerce_event_driven.user.shared.web.ConflictException;
import ecommerce_event_driven.user.shared.web.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/users")
public class UserController {

    private final FindUserByEmailPort findUserByEmail;
    private final CreateUserPort createUser;
    private final IsStoreOwnerPort isStoreOwner;
    private final CurrentUser currentUser;
    private final GetMyProfilePort getMyProfile;
    private final GetPublicProfilePort getPublicProfile;
    private final UpdateMyProfilePort updateMyProfile;
    private final DeleteMyAccountPort deleteMyAccount;

    public UserController(
            FindUserByEmailPort findUserByEmail,
            CreateUserPort createUser,
            IsStoreOwnerPort isStoreOwner,
            CurrentUser currentUser,
            GetMyProfilePort getMyProfile,
            GetPublicProfilePort getPublicProfile,
            UpdateMyProfilePort updateMyProfile,
            DeleteMyAccountPort deleteMyAccount) {
        this.findUserByEmail = findUserByEmail;
        this.createUser = createUser;
        this.isStoreOwner = isStoreOwner;
        this.currentUser = currentUser;
        this.getMyProfile = getMyProfile;
        this.getPublicProfile = getPublicProfile;
        this.updateMyProfile = updateMyProfile;
        this.deleteMyAccount = deleteMyAccount;
    }

    @GetMapping
    public UserResponse getByEmail(@RequestParam String email) {
        return findUserByEmail.findByEmail(email)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("usuario nao encontrado"));
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
            throw new ConflictException("DUPLICATE_USER", "usuario ja existe");
        }
    }

    @GetMapping("/me")
    public MeResponse getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }
        return getMyProfile.getMyProfile(idUser)
                .orElseThrow(() -> new NotFoundException("usuario nao encontrado"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPublicProfile(@PathVariable Long id) {
        return getPublicProfile.getPublicProfile(id)
                .map(response -> ResponseEntity.ok()
                        .header("Cache-Control", "public, max-age=300")
                        .body((Object) response))
                .orElseThrow(() -> new NotFoundException("usuario nao encontrado"));
    }

    @PatchMapping("/me")
    public MeResponse updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateMeRequest request) {
        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }
        return updateMyProfile.updateMyProfile(idUser, request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@AuthenticationPrincipal Jwt jwt) {
        Long idUser = currentUser.extractUserId(jwt);
        if (idUser == null) {
            throw new BadRequestException("usuario id invalido no token");
        }
        deleteMyAccount.deleteMyAccount(idUser);
        return ResponseEntity.noContent().build();
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, isStoreOwner.isStoreOwner(user.id()));
    }

    /** As tres sao NOT NULL no banco. Falha aqui vira 400, nao 500 la embaixo. */
    private static void require(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(field + " e obrigatorio");
        }
    }
}
