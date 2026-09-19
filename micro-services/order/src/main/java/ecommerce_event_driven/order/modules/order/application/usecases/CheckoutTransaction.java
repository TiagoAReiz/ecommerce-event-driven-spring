package ecommerce_event_driven.order.modules.order.application.usecases;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos.CartRepositoryPort;
import ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos.CartItemRepositoryPort;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderItem;

/**
 * Parte transacional do checkout: gravar pedido, limpar carrinho, publicar evento.
 * Tudo acontece numa transacao so.
 */
@Component
public class CheckoutTransaction {
    private final OrderRepositoryPort orderRepo;
    private final CartRepositoryPort cartRepo;
    private final CartItemRepositoryPort itemRepo;
    private final OrderEventPublisherPort publisher;

    public CheckoutTransaction(OrderRepositoryPort orderRepo, CartRepositoryPort cartRepo,
                               CartItemRepositoryPort itemRepo, OrderEventPublisherPort publisher) {
        this.orderRepo = orderRepo;
        this.cartRepo = cartRepo;
        this.itemRepo = itemRepo;
        this.publisher = publisher;
    }

    /**
     * Grava pedido, limpa carrinho e publica order.created - tudo na mesma transacao.
     */
    @Transactional
    public Order executeCheckout(Order order, Long userId) {
        // Gravar o pedido
        Order saved = orderRepo.save(order);

        // Limpar o carrinho
        var cart = cartRepo.findByIdUser(userId);
        if (cart.isPresent()) {
            var items = itemRepo.findByIdCart(cart.get().id());
            for (var item : items) {
                if (item.deletedAt() == null) {
                    itemRepo.save(item.toBuilder().deletedAt(Instant.now()).build());
                }
            }
        }

        // Publicar order.created na outbox
        publisher.publishOrderCreated(saved);

        return saved;
    }
}
