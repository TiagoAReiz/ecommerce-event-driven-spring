package ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.user.application.dtos.MeResponse;
import ecommerce_event_driven.user.modules.user.application.dtos.UpdateMeRequest;

public interface UpdateMyProfilePort {
    MeResponse updateMyProfile(Long idUser, UpdateMeRequest request);
}
