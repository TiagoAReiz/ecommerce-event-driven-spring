package ecommerce_event_driven.user.modules.user.application.usecases;

import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.modules.address.domain.models.Address;
import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.IsStoreOwnerPort;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.DeleteMyAccountPort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.messaging.UserEventPublisherPort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import ecommerce_event_driven.user.shared.web.ConflictException;
import ecommerce_event_driven.user.shared.web.NotFoundException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteMyAccount implements DeleteMyAccountPort {

    private final UserRepositoryPort userRepository;
    private final AddressRepositoryPort addressRepository;
    private final IsStoreOwnerPort isStoreOwner;
    private final UserEventPublisherPort eventPublisher;

    public DeleteMyAccount(
            UserRepositoryPort userRepository,
            AddressRepositoryPort addressRepository,
            IsStoreOwnerPort isStoreOwner,
            UserEventPublisherPort eventPublisher) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.isStoreOwner = isStoreOwner;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void deleteMyAccount(Long idUser) {
        User user = userRepository.findById(idUser)
                .orElseThrow(() -> new NotFoundException("usuario nao encontrado"));

        // Verificar se e o dono da loja
        if (isStoreOwner.isStoreOwner(idUser)) {
            throw new ConflictException("STORE_OWNER_ACCOUNT", "nao pode deletar a conta do dono da loja");
        }

        // Soft delete do usuario
        Instant now = Instant.now();
        User deletedUser = user.toBuilder().deletedAt(now).build();
        userRepository.save(deletedUser);

        // Soft delete de todos os enderecos
        addressRepository.findByIdUser(idUser).forEach(address -> {
            Address deletedAddress = address.toBuilder().deletedAt(now).build();
            addressRepository.save(deletedAddress);
        });

        // Publicar evento na outbox (mesma transacao)
        eventPublisher.publishUserDeleted(idUser, now);
    }
}
