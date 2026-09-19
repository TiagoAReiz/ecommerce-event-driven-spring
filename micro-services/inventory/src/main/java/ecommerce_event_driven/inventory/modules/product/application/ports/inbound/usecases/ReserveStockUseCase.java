package ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases;

import java.util.List;

/**
 * Segura o estoque de um pedido recem-criado, antes do pagamento, e avisa a
 * saga do resultado.
 *
 * <p>Recebe um comando proprio em vez do evento do Kafka: o contrato de fio
 * pertence a infra, e o caso de uso nao deve quebrar quando o evento mudar.
 */
public interface ReserveStockUseCase {

    Result execute(Long idOrder, List<Item> items);

    record Item(Long idProduct, int quantity) {}

    enum Result {
        /** Todos os itens foram segurados. */
        RESERVED,
        /** Faltou disponivel em algum item: nada foi segurado. */
        REJECTED,
        /** Evento repetido: o pedido ja tinha reserva. */
        ALREADY_RESERVED
    }
}
