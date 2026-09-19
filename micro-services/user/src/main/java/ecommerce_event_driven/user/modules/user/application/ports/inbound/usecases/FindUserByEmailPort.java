package ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases;

import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.util.Optional;

public interface FindUserByEmailPort {

    Optional<User> findByEmail(String email);
}
