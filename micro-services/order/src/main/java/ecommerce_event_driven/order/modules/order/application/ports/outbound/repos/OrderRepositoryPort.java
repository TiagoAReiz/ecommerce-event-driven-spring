package ecommerce_event_driven.order.modules.order.application.ports.outbound.repos;

import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Pedido e historico: nao ha remocao, nem logica nem fisica. O encerramento se
 * expressa pelo status (cancelled, refunded).
 *
 * <p>Porta unica do agregado: os itens entram e saem sempre dentro do
 * {@link Order}, nunca por um repositorio proprio. Todo Order devolvido aqui
 * vem com a lista de itens completa.
 */
public interface OrderRepositoryPort {

    /** Grava cabecalho e itens na mesma transacao. */
    Order save(Order order);

    Optional<Order> findById(Long id);

    /** Busca escopada no cliente, para nao vazar pedido de outra pessoa. */
    Optional<Order> findByIdAndIdCustomer(Long id, Long idCustomer);

    Page<Order> findByIdCustomer(Long idCustomer, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Transicao condicional de status.
     *
     * @return false se o pedido ja nao estava em {@code from}
     */
    boolean updateStatus(Long idOrder, OrderStatus from, OrderStatus to);
}
