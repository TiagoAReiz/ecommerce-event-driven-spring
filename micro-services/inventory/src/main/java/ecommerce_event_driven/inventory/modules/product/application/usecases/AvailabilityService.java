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

    public AvailabilityService(StockReservationRepositoryPort reservations) {
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
        Map<Long, Integer> held = reservations.sumHeldByProductIds(List.of(idProduct));
        int heldQty = held.getOrDefault(idProduct, 0);
        return Math.max(0, -heldQty);
    }

    /**
     * Retorna disponibilidades para multiplos produtos em um mapa.
     */
    public Map<Long, Integer> getAvailabilities(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> held = reservations.sumHeldByProductIds(productIds);
        return productIds.stream()
                .collect(java.util.stream.Collectors.toMap(
                        id -> id,
                        id -> Math.max(0, -held.getOrDefault(id, 0))));
    }

    /**
     * Retorna info detalhada de disponibilidade (held + available).
     */
    public DetailedAvailability getDetailedAvailability(Long idProduct) {
        Map<Long, Integer> held = reservations.sumHeldByProductIds(List.of(idProduct));
        int heldQty = held.getOrDefault(idProduct, 0);
        return new DetailedAvailability(heldQty, Math.max(0, -heldQty));
    }

    public record DetailedAvailability(int held, int available) {}
}
