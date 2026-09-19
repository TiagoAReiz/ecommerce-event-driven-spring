package ecommerce_event_driven.user.modules.user.application.dtos;

/**
 * Campos editaveis do perfil. email e googleSub sao imutaveis e devem
 * ser recusados se aparecerem no corpo.
 */
public record UpdateMeRequest(String name, String cpf, String phone, String photoUrl) {
}
