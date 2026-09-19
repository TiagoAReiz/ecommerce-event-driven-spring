package ecommerce_event_driven.order.modules.order.domain.models;

/**
 * Maquina de estados para o pedido.
 * Define as transicoes validas entre status.
 *
 * A tabela de transicoes vem de docs/event-contracts.md 3.1.
 */
public class OrderStateMachine {

    /**
     * Verifica se uma transicao de status e valida.
     */
    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }

        return switch (from) {
            // pending pode ir para paid ou cancelled
            case pending -> to == OrderStatus.paid || to == OrderStatus.cancelled;

            // paid pode ir para processing ou cancelled
            case paid -> to == OrderStatus.processing || to == OrderStatus.cancelled;

            // processing pode ir para cancelled, shipped ou delivered
            case processing -> to == OrderStatus.cancelled || to == OrderStatus.shipped || to == OrderStatus.delivered;

            // shipped pode ir para delivered
            case shipped -> to == OrderStatus.delivered;

            // delivered pode ir para refunded
            case delivered -> to == OrderStatus.refunded;

            // cancelled e refunded sao estados finais
            case cancelled, refunded -> false;
        };
    }
}
