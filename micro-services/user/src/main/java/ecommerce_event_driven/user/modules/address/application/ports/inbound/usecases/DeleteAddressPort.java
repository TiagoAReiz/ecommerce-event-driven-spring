package ecommerce_event_driven.user.modules.address.application.ports.inbound.usecases;

public interface DeleteAddressPort {
    void deleteAddress(Long idUser, Long idAddress);
}
