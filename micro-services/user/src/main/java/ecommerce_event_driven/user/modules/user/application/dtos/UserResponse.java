package ecommerce_event_driven.user.modules.user.application.dtos;

import ecommerce_event_driven.user.modules.user.domain.models.User;
import java.util.List;

public record UserResponse(
        Long id,
        String name,
        String email,
        String googleSub,
        String photoUrl,
        String cpf,
        String phone,
        /** Todo usuario e customer; owner so a conta da loja. O gateway le isto no login. */
        List<String> roles) {

    private static final List<String> CUSTOMER = List.of("customer");
    private static final List<String> STORE_OWNER = List.of("customer", "owner");

    public static UserResponse from(User user, boolean storeOwner) {
        return new UserResponse(
                user.id(),
                user.name(),
                user.email(),
                user.googleSub(),
                user.photoUrl(),
                user.cpf(),
                user.phone(),
                storeOwner ? STORE_OWNER : CUSTOMER);
    }
}
