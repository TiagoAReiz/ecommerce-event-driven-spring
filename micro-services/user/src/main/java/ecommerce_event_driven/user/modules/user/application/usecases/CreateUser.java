package ecommerce_event_driven.user.modules.user.application.usecases;

import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.ProvisionStoreOwnerPort;
import ecommerce_event_driven.user.modules.user.application.dtos.CreateUserRequest;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.CreateUserPort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateUser implements CreateUserPort {

    private final UserRepositoryPort users;
    private final ProvisionStoreOwnerPort provisionStoreOwner;

    public CreateUser(UserRepositoryPort users, ProvisionStoreOwnerPort provisionStoreOwner) {
        this.users = users;
        this.provisionStoreOwner = provisionStoreOwner;
    }

    /**
     * Deixa a DataIntegrityViolationException subir de proposito: os indices
     * users_email_uk e users_google_sub_uk sao a defesa contra duplicata, e o
     * controller traduz isso para 409.
     *
     * <p>Transacional porque o primeiro login do dono da loja cria duas linhas,
     * users e owner: usuario da loja sem owner nao consegue operar a loja.
     */
    @Override
    @Transactional
    public User create(CreateUserRequest request) {
        User user = users.save(User.builder()
                .name(request.name())
                .email(request.email())
                .googleSub(request.googleSub())
                .photoUrl(request.photoUrl())
                .build());

        provisionStoreOwner.provisionIfStoreOwner(user.id(), user.email());
        return user;
    }
}
