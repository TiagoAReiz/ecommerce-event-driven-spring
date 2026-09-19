package ecommerce_event_driven.user.modules.address.application.dtos;

/**
 * Requisicao para criar um endereco. Campos obrigatorios:
 * zipcode (8 digitos), state (2 letras para BR), city, street.
 * Opcionais: name, country (default BR), number.
 */
public record CreateAddressRequest(
        String name,
        String zipcode,
        String country,
        String state,
        String city,
        String street,
        String number) {
}
