package ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.user.application.dtos.PublicUserResponse;
import java.util.Optional;

public interface GetPublicProfilePort {
    Optional<PublicUserResponse> getPublicProfile(Long idUser);
}
