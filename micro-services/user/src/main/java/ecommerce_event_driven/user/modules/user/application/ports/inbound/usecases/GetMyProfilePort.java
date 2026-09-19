package ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.user.application.dtos.MeResponse;
import java.util.Optional;

public interface GetMyProfilePort {
    Optional<MeResponse> getMyProfile(Long idUser);
}
