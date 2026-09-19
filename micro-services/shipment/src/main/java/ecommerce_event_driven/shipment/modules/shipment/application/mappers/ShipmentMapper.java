package ecommerce_event_driven.shipment.modules.shipment.application.mappers;

import ecommerce_event_driven.shipment.modules.shipment.domain.models.AddressSnapshot;
import ecommerce_event_driven.shipment.modules.shipment.domain.models.Shipment;
import ecommerce_event_driven.shipment.modules.shipment.infra.outbound.repos.entity.ShipmentEntity;

public final class ShipmentMapper {

    private ShipmentMapper() {
    }

    public static Shipment toDomain(ShipmentEntity entity) {
        if (entity == null) {
            return null;
        }
        return Shipment.builder()
                .id(entity.getId())
                .idOrder(entity.getIdOrder())
                .idUser(entity.getIdUser())
                .idAddressUser(entity.getIdAddressUser())
                .status(entity.getStatus())
                .freightTax(entity.getFreightTax())
                .trackingCode(entity.getTrackingCode())
                .destination(AddressSnapshot.builder()
                        .zipcode(entity.getToZipcode())
                        .country(entity.getToCountry())
                        .state(entity.getToState())
                        .city(entity.getToCity())
                        .street(entity.getToStreet())
                        .number(entity.getToNumber())
                        .build())
                .origin(AddressSnapshot.builder()
                        .zipcode(entity.getFromZipcode())
                        .country(entity.getFromCountry())
                        .state(entity.getFromState())
                        .city(entity.getFromCity())
                        .street(entity.getFromStreet())
                        .number(entity.getFromNumber())
                        .build())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public static ShipmentEntity toEntity(Shipment model) {
        if (model == null) {
            return null;
        }
        AddressSnapshot to = model.destination();
        AddressSnapshot from = model.origin();
        ShipmentEntity.ShipmentEntityBuilder builder = ShipmentEntity.builder()
                .id(model.id())
                .idOrder(model.idOrder())
                .idUser(model.idUser())
                .idAddressUser(model.idAddressUser())
                .status(model.status())
                .freightTax(model.freightTax())
                .trackingCode(model.trackingCode())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .deletedAt(model.deletedAt());
        if (to != null) {
            builder.toZipcode(to.zipcode())
                    .toCountry(to.country())
                    .toState(to.state())
                    .toCity(to.city())
                    .toStreet(to.street())
                    .toNumber(to.number());
        }
        if (from != null) {
            builder.fromZipcode(from.zipcode())
                    .fromCountry(from.country())
                    .fromState(from.state())
                    .fromCity(from.city())
                    .fromStreet(from.street())
                    .fromNumber(from.number());
        }
        return builder.build();
    }
}
