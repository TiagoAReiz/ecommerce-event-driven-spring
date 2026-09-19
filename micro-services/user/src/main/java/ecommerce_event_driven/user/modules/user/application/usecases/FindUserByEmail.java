package ecommerce_event_driven.user.modules.user.application.usecases;

import ecommerce_event_driven.user.modules.user.application.ports.inbound.usecases.FindUserByEmailPort;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FindUserByEmail implements FindUserByEmailPort {

    private final UserRepositoryPort users;

    public FindUserByEmail(UserRepositoryPort users) {
        this.users = users;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.findByEmail(email);
    }
}
