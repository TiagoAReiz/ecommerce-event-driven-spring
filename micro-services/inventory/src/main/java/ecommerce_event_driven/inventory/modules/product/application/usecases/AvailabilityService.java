package ecommerce_event_driven.inventory.modules.product.application.usecases;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.StockReservationRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.models.Product;

/**
 * Calcula disponibilidade em tempo real: estoque bruto menos reservas held ativas.
 * NUNCA cacheado: sempre consulta o banco para verificar quais reservas ainda estao validas.
 */
@Service
public class AvailabilityService {

    private final StockReservationRepositoryPort reservations;
    private final ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository products;

    public AvailabilityService(
            StockReservationRepositoryPort reservations,
            ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository products) {
        this.products = products;
        this.reservations = reservations;
    }

    /**
     * Calcula disponibilidade para um produto.
     * disponivel = stock - soma de reservas held nao expiradas
     */
    public long calculateAvailable(Product product) {
        return calculateAvailable(product.id(), product.stock());
    }

    public long calculateAvailable(Long idProduct, int stock) {
        Map<Long, Integer> held = reservations.sumHeldByProductIds(List.of(idProduct));
        int heldQuantity = held.getOrDefault(idProduct, 0);
        return Math.max(0, stock - heldQuantity);
    }

    /**
     * Calcula disponibilidade em lote para multiplos produtos.
     * @return mapa de idProduct -> disponivel
     */
    public Map<Long, Long> calculateAvailableBatch(Map<Long, Integer> productStocks) {
        Map<Long, Integer> held = reservations.sumHeldByProductIds(List.copyOf(productStocks.keySet()));

        return productStocks.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Math.max(0L, entry.getValue() - held.getOrDefault(entry.getKey(), 0))));
    }

    /**
     * Retorna a disponibilidade simples para um produto (int).
     */
    public int getAvailability(Long idProduct) {
        Integer stock = products.findById(idProduct)
                .filter(product -> product.getDeletedAt() == null)
                .map(product -> product.getStock())
                .orElse(0);
        Map<Long, Integer> held = reservations.sumHeldByProductIds(List.of(idProduct));
        // Disponivel = estoque - o que esta segurado por reserva ainda valida.
        return Math.max(0, stock - held.getOrDefault(idProduct, 0));
    }

    /**
     * Retorna disponibilidades para multiplos produtos em um mapa.
     */
    public Map<Long, Integer> getAvailabilities(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> held = reservations.sumHeldByProductIds(productIds);
        // Disponivel = estoque - o que esta segurado por reserva ainda valida.
        // Sem o estoque na conta, a vitrine inteira aparecia esgotada.
        Map<Long, Integer> stocks = products.findAllById(productIds).stream()
                .filter(product -> product.getDeletedAt() == null)
                .collect(java.util.stream.Collectors.toMap(
                        product -> product.getId(),
                        product -> product.getStock() == null ? 0 : product.getStock()));
        return productIds.stream()
                .collect(java.util.stream.Collectors.toMap(
                        id -> id,
                        id -> Math.max(0, stocks.getOrDefault(id, 0) - held.getOrDefault(id, 0))));
    }

    /**
     * Retorna info detalhada de disponibilidade (held + available).
     */
    public DetailedAvailability getDetailedAvailability(Long idProduct) {
        Integer stock = products.findById(idProduct)
                .filter(product -> product.getDeletedAt() == null)
                .map(product -> product.getStock())
                .orElse(0);
        Map<Long, Integer> held = reservations.sumHeldByProductIds(List.of(idProduct));
        int heldQty = held.getOrDefault(idProduct, 0);
        return new DetailedAvailability(heldQty, Math.max(0, stock - heldQty));
    }

    public record DetailedAvailability(int held, int available) {}
}
