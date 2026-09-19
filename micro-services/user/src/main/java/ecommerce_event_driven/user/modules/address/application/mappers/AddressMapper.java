package ecommerce_event_driven.user.modules.address.application.mappers;

import ecommerce_event_driven.user.modules.address.domain.models.Address;
import ecommerce_event_driven.user.modules.address.infra.outbound.repos.entity.AddressEntity;

public final class AddressMapper {

    private AddressMapper() {
    }

    public static Address toDomain(AddressEntity entity) {
        if (entity == null) {
            return null;
        }
        return Address.builder()
                .id(entity.getId())
                .idUser(entity.getIdUser())
                .name(entity.getName())
                .zipcode(entity.getZipcode())
                .country(entity.getCountry())
                .state(entity.getState())
                .city(entity.getCity())
                .street(entity.getStreet())
                .number(entity.getNumber())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static AddressEntity toEntity(Address model) {
        if (model == null) {
            return null;
        }
        return AddressEntity.builder()
                .id(model.id())
                .idUser(model.idUser())
                .name(model.name())
                .zipcode(model.zipcode())
                .country(model.country())
                .state(model.state())
                .city(model.city())
                .street(model.street())
                .number(model.number())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt())
                .build();
    }
}
