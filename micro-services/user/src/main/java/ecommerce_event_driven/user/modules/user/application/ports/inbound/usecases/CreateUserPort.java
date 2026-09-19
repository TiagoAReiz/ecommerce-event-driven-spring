package ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.user.application.dtos.CreateUserRequest;
import ecommerce_event_driven.user.modules.user.domain.models.User;

public interface CreateUserPort {

    User create(CreateUserRequest request);
}
