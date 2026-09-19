package ecommerce_event_driven.user.modules.address.application.dtos;

import ecommerce_event_driven.user.modules.address.domain.models.Address;

/**
 * Endereco completo para rotas internas (pode conter soft-deleted).
 * Usado por order/shipment para validar historico.
 */
public record InternalAddressResponse(
        Long id,
        Long idUser,
        String name,
        String zipcode,
        String country,
        String state,
        String city,
        String street,
        String number) {

    public static InternalAddressResponse from(Address address) {
        return new InternalAddressResponse(
                address.id(),
                address.idUser(),
                address.name(),
                address.zipcode(),
                address.country(),
                address.state(),
                address.city(),
                address.street(),
                address.number());
    }
}
