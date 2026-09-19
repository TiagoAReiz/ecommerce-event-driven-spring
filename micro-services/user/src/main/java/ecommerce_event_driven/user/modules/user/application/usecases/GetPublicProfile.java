package ecommerce_event_driven.user.modules.user.application.usecases;

import ecommerce_event_driven.user.modules.user.application.dtos.PublicUserResponse;
import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.GetPublicProfilePort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetPublicProfile implements GetPublicProfilePort {

    private final UserRepositoryPort userRepository;

    public GetPublicProfile(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<PublicUserResponse> getPublicProfile(Long idUser) {
        return userRepository.findById(idUser).map(PublicUserResponse::from);
    }
}
