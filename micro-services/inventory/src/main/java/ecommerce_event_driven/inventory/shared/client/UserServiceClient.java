package ecommerce_event_driven.inventory.shared.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente para chamar o servico de usuario com token de servico.
 * Todos os servidores-a-servidor usam esse token (escopo internal:hydrate).
 */
@Component
public class UserServiceClient {

    private final RestClient userClient;
    private final ServiceTokenProvider tokenProvider;

    public UserServiceClient(
            RestClient userClient,
            ServiceTokenProvider tokenProvider) {

        this.userClient = userClient;
        this.tokenProvider = tokenProvider;
    }

    /**
     * GET /internal/users?ids=1,2 -> {users:[...],missing:[]}
     */
    public UserBatchResponse getUsersById(List<Long> ids) {
        String idList = String.join(",", ids.stream().map(String::valueOf).toList());
        return userClient
                .get()
                .uri("/internal/users?ids={ids}", idList)
                .retrieve()
                .body(UserBatchResponse.class);
    }

    /**
     * GET /internal/users/{id}/profile -> {id,name,email,photoUrl,roles}
     */
    public UserProfileResponse getUserProfile(Long userId) {
        return userClient
                .get()
                .uri("/internal/users/{id}/profile", userId)
                .retrieve()
                .body(UserProfileResponse.class);
    }

    record UserBatchResponse(List<UserInfo> users, List<Long> missing) {
    }

    record UserInfo(Long id, String name, String photoUrl) {
    }

    record UserProfileResponse(Long id, String name, String email, String photoUrl, List<String> roles) {
    }
}
