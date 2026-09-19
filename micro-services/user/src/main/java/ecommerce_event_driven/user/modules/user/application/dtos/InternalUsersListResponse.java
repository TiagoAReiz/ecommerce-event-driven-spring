package ecommerce_event_driven.user.modules.user.application.dtos;

import java.util.List;

/**
 * Resposta de hidratacao em lote: usuarios encontrados e ids faltantes.
 */
public record InternalUsersListResponse(List<InternalUserResponse> users, List<Long> missing) {
}
