package ecommerce_event_driven.user.modules.address.application.dtos;

/**
 * Requisicao para atualizar um endereco. Todos os campos sao opcionais.
 * PATCH: campos nao fornecidos herdam o valor anterior.
 * PUT: campos nao fornecidos viram null.
 */
public record UpdateAddressRequest(
        String name,
        String zipcode,
        String country,
        String state,
        String city,
        String street,
        String number) {
}
