package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.ReserveStockUseCase.Item;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.ReserveStockUseCase.Result;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.StockReservationRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.exceptions.InsufficientStockException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parte transacional da reserva. Classe propria porque o Spring so intercepta
 * @Transactional em chamada entre beans: metodo interno do service nao abriria
 * transacao nenhuma.
 */
@Component
class StockHoldTransaction {

    /** Por quanto tempo a reserva segura o estoque de um pedido nao pago. */
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StockReservationRepositoryPort reservations;

    StockHoldTransaction(StockReservationRepositoryPort reservations) {
        this.reservations = reservations;
    }

    /** Tudo ou nada: se faltar estoque no ultimo item, o rollback desfaz os anteriores. */
    @Transactional
    Result hold(Long idOrder, List<Item> items) {
        if (reservations.existsByIdOrder(idOrder)) {
            return Result.ALREADY_RESERVED;
        }

        Instant expiresAt = Instant.now().plus(TTL);

        // Ordem global fixa por id: sem isso, dois pedidos com os mesmos
        // produtos em ordens diferentes travam um no outro (deadlock).
        List<Item> ordered = items.stream()
                .sorted(Comparator.comparing(Item::idProduct))
                .toList();

        for (Item item : ordered) {
            boolean held = reservations.hold(idOrder, item.idProduct(), item.quantity(), expiresAt);
            if (!held) {
                throw new InsufficientStockException(idOrder, item.idProduct(), item.quantity());
            }
        }

        return Result.RESERVED;
    }
}
