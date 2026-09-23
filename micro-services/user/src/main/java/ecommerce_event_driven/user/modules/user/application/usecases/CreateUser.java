package ecommerce_event_driven.user.modules.user.application.usecases;

import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.ProvisionStoreOwnerPort;
import ecommerce_event_driven.user.modules.user.application.dtos.CreateUserRequest;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.CreateUserPort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.storage.AvatarStoragePort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateUser implements CreateUserPort {

    private final UserRepositoryPort users;
    private final ProvisionStoreOwnerPort provisionStoreOwner;
    private final AvatarStoragePort avatares;

    public CreateUser(
            UserRepositoryPort users,
            ProvisionStoreOwnerPort provisionStoreOwner,
            AvatarStoragePort avatares) {
        this.users = users;
        this.provisionStoreOwner = provisionStoreOwner;
        this.avatares = avatares;
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
                .build());

        // A foto vem do CDN do Google, que recusa a imagem pedida a partir de outro
        // dominio e ainda troca a URL com o tempo. Guardamos uma copia nossa; se a
        // copia falhar, a conta fica sem foto (a tela mostra a inicial do nome) --
        // perder o login por causa de um avatar seria pior.
        String foto = avatares.guardar(user.id(), request.photoUrl());
        if (foto != null) {
            user = users.save(user.toBuilder().photoUrl(foto).build());
        }

        provisionStoreOwner.provisionIfStoreOwner(user.id(), user.email());
        return user;
    }
}
