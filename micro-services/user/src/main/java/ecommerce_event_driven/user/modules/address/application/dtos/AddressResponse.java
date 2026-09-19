package ecommerce_event_driven.user.modules.address.application.dtos;

import ecommerce_event_driven.user.modules.address.domain.models.Address;
import java.time.Instant;

public record AddressResponse(
        Long id,
        String name,
        String zipcode,
        String country,
        String state,
        String city,
        String street,
        String number,
        Instant createdAt) {

    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.id(),
                address.name(),
                address.zipcode(),
                address.country(),
                address.state(),
                address.city(),
                address.street(),
                address.number(),
                address.createdAt());
    }
}
