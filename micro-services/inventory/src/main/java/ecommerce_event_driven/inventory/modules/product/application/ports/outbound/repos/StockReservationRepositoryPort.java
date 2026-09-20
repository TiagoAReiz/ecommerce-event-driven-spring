package ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos;

import java.time.Instant;
import java.util.Map;

/**
 * Porta do estoque reservado. As operacoes sao do dominio, nao CRUD.
 *
 * <p>Nao existe operacao de liberar reserva vencida: a reserva expira sozinha,
 * porque a disponibilidade e calculada ignorando as que passaram do prazo.
 */
public interface StockReservationRepositoryPort {

    /**
     * Segura {@code quantity} do produto, se houver disponivel.
     *
     * @return false quando stock - reservado ativo nao cobre a quantidade
     */
    boolean hold(Long idOrder, Long idProduct, int quantity, Instant expiresAt);

    /** Guarda de idempotencia: o mesmo OrderCreated pode chegar duas vezes. */
    boolean existsByIdOrder(Long idOrder);

    /**
     * Marca como released as reservas ainda seguradas do pedido.
     *
     * @return quantas foram liberadas; 0 quando nao havia reserva ativa
     */
    int releaseHeldByOrder(Long idOrder);

    /**
     * Retorna a soma de quantidade reservada (held) e nao expirada para cada produto.
     * Reservas com expires_at <= now() nao entram na soma.
     * @param productIds lista de IDs de produtos
     * @return mapa de idProduct -> quantidade reservada (0 se nenhuma reserva ativa)
     */
    Map<Long, Integer> sumHeldByProductIds(java.util.List<Long> productIds);

    /**
     * Muda status de reserva do pedido de held para confirmed.
     * Mesmo que a reserva tenha expirado, o commit acontece se houver disponivel.
     * @return true se atualizou, false se nenhuma reserva estava held
     */
    boolean confirmHeldByOrder(Long idOrder);

    /**
     * Muda status de reserva do pedido de confirmed para released,
     * junto com incremento de estoque do produto.
     * @return true se atualizou, false se nenhuma reserva estava confirmed
     */
    boolean releaseConfirmedByOrder(Long idOrder);

    /**
     * Encontra reservas held ativas do pedido, mesmo expiradas.
     * @return lista de [idProduct, quantity] para reservas held do pedido
     */
    java.util.List<Object[]> findHeldByOrder(Long idOrder);
}
