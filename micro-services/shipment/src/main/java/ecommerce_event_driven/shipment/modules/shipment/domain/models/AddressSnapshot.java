package ecommerce_event_driven.shipment.modules.shipment.domain.models;

import lombok.Builder;

/**
 * Copia imutavel de um endereco no momento do envio. O destino vem do servico
 * user e a origem da configuracao da loja: os dois podem mudar depois, e a
 * remessa nao pode mudar de endereco retroativamente.
 * Na tabela isso esta achatado nos prefixos to_ (destino) e from_ (origem).
 * Sem id: a origem nao tem um, e o do destino fica em Shipment.idAddressUser.
 */
@Builder(toBuilder = true)
public record AddressSnapshot(
        String zipcode,
        String country,
        String state,
        String city,
        String street,
        String number) {
}
