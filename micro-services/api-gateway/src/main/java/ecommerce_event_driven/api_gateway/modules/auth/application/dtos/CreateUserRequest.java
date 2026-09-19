package ecommerce_event_driven.api_gateway.modules.auth.application.dtos;

/**
 * Corpo do POST /users no microservico user.
 *
 * <p>googleSub e o id do usuario no Google. E NOT NULL e unico do outro lado,
 * porque e ele que identifica a conta de verdade: o email pode ser trocado
 * numa conta Google, o sub nao.
 */
public record CreateUserRequest(
        String name,
        String email,
        String googleSub,
        String photoUrl) {
}
