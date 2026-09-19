package ecommerce_event_driven.user.modules.owner.infra.inbound.startup;

import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.ProvisionStoreOwnerPort;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Provisiona o dono da loja a cada boot.
 *
 * <p>O primeiro login ja cria o owner (CreateUser). Isto cobre o que o login nao
 * ve: o usuario que ja existia quando a configuracao passou a apontar para ele,
 * e a troca de dono feita trocando app.store.owner-email.
 */
@Component
public class StoreOwnerStartup implements ApplicationRunner {

    private final ProvisionStoreOwnerPort provisionStoreOwner;

    public StoreOwnerStartup(ProvisionStoreOwnerPort provisionStoreOwner) {
        this.provisionStoreOwner = provisionStoreOwner;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisionStoreOwner.provisionAtStartup();
    }
}
