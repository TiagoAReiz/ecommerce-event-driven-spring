package ecommerce_event_driven.user.modules.owner.application.usecases;

import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.IsStoreOwnerPort;
import ecommerce_event_driven.user.modules.owner.application.ports.outbound.repos.OwnerRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class IsStoreOwner implements IsStoreOwnerPort {

    private final OwnerRepositoryPort owners;

    public IsStoreOwner(OwnerRepositoryPort owners) {
        this.owners = owners;
    }

    @Override
    public boolean isStoreOwner(Long idUser) {
        return owners.findByIdUser(idUser).isPresent();
    }
}
