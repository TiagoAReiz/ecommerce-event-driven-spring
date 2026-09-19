package ecommerce_event_driven.user.modules.user.application.usecases;

import ecommerce_event_driven.user.modules.address.application.ports.outbound.repos.AddressRepositoryPort;
import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.IsStoreOwnerPort;
import ecommerce_event_driven.user.modules.user.application.dtos.MeResponse;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.GetMyProfilePort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetMyProfile implements GetMyProfilePort {

    private final UserRepositoryPort userRepository;
    private final AddressRepositoryPort addressRepository;
    private final IsStoreOwnerPort isStoreOwner;

    public GetMyProfile(
            UserRepositoryPort userRepository,
            AddressRepositoryPort addressRepository,
            IsStoreOwnerPort isStoreOwner) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.isStoreOwner = isStoreOwner;
    }

    @Override
    public Optional<MeResponse> getMyProfile(Long idUser) {
        return userRepository.findById(idUser).map(user -> {
            long addressCount = addressRepository.findByIdUser(idUser).size();
            boolean isOwner = isStoreOwner.isStoreOwner(idUser);
            return MeResponse.from(user, isOwner, addressCount);
        });
    }
}
