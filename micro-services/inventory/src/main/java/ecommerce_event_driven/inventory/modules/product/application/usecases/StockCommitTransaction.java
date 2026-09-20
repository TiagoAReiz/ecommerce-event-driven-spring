package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.CommitStockUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.messaging.StockEventPublisherPort;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.StockReservationRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.models.ReservationStatus;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.StockReservationJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductEntity;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.StockReservationEntity;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transacao de commit de estoque: tudo-ou-nada.
 *
 * <p>Trava produtos em ordem de id para evitar deadlock, confirma reservas
 * (mesmo vencidas se houver disponivel) e debita estoque. Se faltar para algum
 * item, o rollback desfaz tudo.
 */
@Component
class StockCommitTransaction {

    private final StockReservationJpaRepository jpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final StockEventPublisherPort publisher;

    StockCommitTransaction(
            StockReservationJpaRepository jpaRepository,
            ProductJpaRepository productJpaRepository,
            StockEventPublisherPort publisher) {
        this.jpaRepository = jpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.publisher = publisher;
    }

    /**
     * Tenta baixar estoque do pedido.
     *
     * @return se conseguiu commit, em que estado terminou, e qual produto falhou
     */
    @Transactional
    CommitStockService.CommitResult commit(Long idOrder, List<CommitStockUseCase.Item> items) {
        // Verifica se ja foi commitado (reentrega idempotente)
        List<StockReservationEntity> existing = jpaRepository.findByIdOrder(idOrder);
        if (!existing.isEmpty() && existing.stream().allMatch(r -> r.getStatus() == ReservationStatus.confirmed)) {
            return CommitStockService.CommitResult.ALREADY_COMMITTED;
        }

        // Se alguma reserva ja foi released, falha
        if (existing.stream().anyMatch(r -> r.getStatus() == ReservationStatus.released)) {
            return CommitStockService.CommitResult.COMMIT_FAILED_RESERVATION_RELEASED;
        }

        // Ordena por product id para evitar deadlock
        List<CommitStockUseCase.Item> ordered = items.stream()
                .sorted(Comparator.comparing(CommitStockUseCase.Item::productId))
                .toList();

        // Processa cada item: lock, verifica, cria ou atualiza reserva, debita estoque
        for (CommitStockUseCase.Item item : ordered) {
            CommitStockService.CommitResult result = processItem(idOrder, item);
            if (result != CommitStockService.CommitResult.OK) {
                return result;
            }
        }

        // O evento sai na mesma transacao da baixa: a outbox so aceita escrita dentro de
        // transacao, e publicar depois do commit abriria a janela de estoque baixado sem
        // stock.committed -- a reentrega cairia em ALREADY_COMMITTED e o evento sumiria.
        publisher.publishStockCommitted(idOrder);
        return CommitStockService.CommitResult.OK;
    }

    private CommitStockService.CommitResult processItem(Long idOrder, CommitStockUseCase.Item item) {
        // Trava o produto
        Optional<ProductEntity> locked = productJpaRepository.findByIdForUpdate(item.productId());
        if (locked.isEmpty()) {
            return CommitStockService.CommitResult.COMMIT_FAILED_PRODUCT_NOT_FOUND;
        }

        ProductEntity product = locked.get();

        // Procura reserva existente do pedido para este produto
        Optional<StockReservationEntity> existingReservation = jpaRepository.findByIdOrder(idOrder)
                .stream()
                .filter(r -> r.getIdProduct().equals(item.productId()))
                .findFirst();

        // Calcula disponibilidade: stock - outras reservas held ativas
        int reservedByOthers = jpaRepository.sumActiveQuantity(
                item.productId(), ReservationStatus.held, Instant.now());

        // Se existe reserva deste pedido, nao conta contra si mesmo
        if (existingReservation.isPresent() && existingReservation.get().getStatus() == ReservationStatus.held) {
            reservedByOthers -= existingReservation.get().getQuantity();
        }

        int available = product.getStock() - reservedByOthers;

        if (available < item.quantity()) {
            return CommitStockService.CommitResult.COMMIT_FAILED_EXPIRED_WITHOUT_STOCK;
        }

        // Se tem reserva held, confirma
        if (existingReservation.isPresent()) {
            StockReservationEntity reservation = existingReservation.get();
            if (reservation.getStatus() == ReservationStatus.held) {
                reservation.setStatus(ReservationStatus.confirmed);
                jpaRepository.save(reservation);
            }
        } else {
            // Sem reserva: cria confirmed direto
            jpaRepository.save(StockReservationEntity.builder()
                    .idOrder(idOrder)
                    .idProduct(item.productId())
                    .quantity(item.quantity())
                    .status(ReservationStatus.confirmed)
                    .expiresAt(Instant.now().plusSeconds(1)) // Nao expira mais
                    .build());
        }

        // Debita estoque
        productJpaRepository.decrementStock(item.productId(), item.quantity());

        return CommitStockService.CommitResult.OK;
    }
}
