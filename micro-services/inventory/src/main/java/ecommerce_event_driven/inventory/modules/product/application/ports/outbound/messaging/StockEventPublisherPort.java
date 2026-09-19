package ecommerce_event_driven.inventory.modules.product.application.ports.outbound.messaging;

/** Resposta do inventory ao order: a saga precisa saber se pode seguir. */
public interface StockEventPublisherPort {

    void publishStockReserved(Long idOrder);

    void publishStockRejected(Long idOrder, Long idProduct);
}
