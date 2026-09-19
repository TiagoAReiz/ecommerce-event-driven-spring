package ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases;

/** Devolve ao disponivel o estoque de um pedido que foi cancelado. */
public interface ReleaseReservationUseCase {

    Result execute(Long idOrder);

    enum Result {
        RELEASED,
        /** Pedido sem reserva ativa: nunca reservou, ja venceu ou ja foi paga. */
        NOTHING_TO_RELEASE
    }
}
