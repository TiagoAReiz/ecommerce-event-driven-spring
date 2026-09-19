package ecommerce_event_driven.user.modules.user.application.dtos;

/**
 * Corpo do POST /users.
 *
 * <p>cpf e phone nao entram: sao opcionais no schema e sao pedidos depois,
 * quando o usuario for fechar o primeiro pedido.
 */
public record CreateUserRequest(
        String name,
        String email,
        String googleSub,
        String photoUrl) {
}
