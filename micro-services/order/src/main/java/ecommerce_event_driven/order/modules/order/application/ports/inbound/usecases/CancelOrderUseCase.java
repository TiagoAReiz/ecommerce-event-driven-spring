package ecommerce_event_driven.order.modules.order.application.ports.inbound.usecases;

/** Cancela um pedido que ainda nao foi pago e avisa quem depende disso. */
public interface CancelOrderUseCase {

    Result execute(Long idOrder, String reason);

    enum Result {
        CANCELLED,
        /** Pedido ja estava cancelado, ou ja tinha saido de pending. */
        NOT_APPLICABLE
    }
}
