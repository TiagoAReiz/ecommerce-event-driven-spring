package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.modules.order.application.dtos.OrderListResponse;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderListResponse.FirstItem;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderListResponse.OrderSummary;
import ecommerce_event_driven.order.modules.order.application.dtos.OrderListResponse.PageMeta;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Lista todos os pedidos (para gestao da loja).
 */
@Service
public class ListStoreOrdersUseCase {
    private final OrderRepositoryPort orderRepo;

    public ListStoreOrdersUseCase(OrderRepositoryPort orderRepo) {
        this.orderRepo = orderRepo;
    }

    /**
     * Lista todos os pedidos com filtros opcionais.
     * @param statuses Filtros de status (opcional)
     * @param customerId Filtro por cliente (opcional)
     * @param from Data de inicio (inclusive)
     * @param to Data de fim (inclusive)
     * @param page Numero da pagina (0-indexed)
     * @param size Tamanho da pagina
     * @param sort Campo e direcao (ex: "createdAt,desc")
     * @return Pagina de pedidos
     */
    public OrderListResponse execute(List<OrderStatus> statuses, Long customerId, LocalDate from, LocalDate to,
                                     Integer page, Integer size, String sort) {
        // Default values
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 20;
        if (sort == null || sort.isEmpty()) sort = "createdAt,desc";

        // Parse sort (format: "field,direction")
        String[] sortParts = sort.split(",");
        String sortField = sortParts.length > 0 ? sortParts[0] : "createdAt";
        String sortDir = sortParts.length > 1 ? sortParts[1].toUpperCase() : "DESC";
        Sort.Direction direction = Sort.Direction.DESC;
        try {
            direction = Sort.Direction.valueOf(sortDir);
        } catch (IllegalArgumentException e) {
            direction = Sort.Direction.DESC;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        // Query database - for now get all orders, filtering in memory
        // A proper implementation would push filters to the database
        Page<Order> result = null;
        if (statuses != null && statuses.size() == 1) {
            // If only one status, we can query by status
            result = orderRepo.findByStatus(statuses.get(0), pageable);
        } else {
            // Otherwise get all and filter in memory
            result = orderRepo.findByIdCustomer(null, pageable);
        }

        // If result is null or if we need to filter further
        if (result == null || (customerId != null || (statuses != null && statuses.size() != 1)) ||
            from != null || to != null) {
            // Fallback: this is a limitation of the current repository
            // In a real scenario, the repository would support filtering by multiple criteria
            result = orderRepo.findByStatus(statuses != null && !statuses.isEmpty() ? statuses.get(0) : null, pageable);
        }

        // Filter and map results
        List<Order> filtered = result.getContent();

        if (customerId != null) {
            filtered = filtered.stream()
                    .filter(o -> o.idCustomer().equals(customerId))
                    .toList();
        }

        if (statuses != null && !statuses.isEmpty()) {
            filtered = filtered.stream()
                    .filter(o -> statuses.contains(o.status()))
                    .toList();
        }

        if (from != null || to != null) {
            Instant fromInstant = from != null ? from.atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.EPOCH;
            Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.now();

            filtered = filtered.stream()
                    .filter(o -> o.createdAt() != null && o.createdAt().isAfter(fromInstant) && o.createdAt().isBefore(toInstant))
                    .toList();
        }

        // Map to response with idCustomer included
        List<OrderSummary> summaries = filtered.stream()
                .map(this::toSummary)
                .toList();

        PageMeta pageMeta = new PageMeta(
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages()
        );

        return new OrderListResponse(summaries, pageMeta);
    }

    private OrderSummary toSummary(Order order) {
        FirstItem firstItem = null;
        if (!order.items().isEmpty()) {
            var item = order.items().get(0);
            firstItem = new FirstItem(item.productName(), item.productPhotoUrl());
        }

        return new OrderSummary(
                order.id(),
                order.status().name(),
                order.itemsCost(),
                order.freightCost(),
                order.totalCost(),
                order.items().size(),
                firstItem,
                order.idCustomer(),  // Include customer ID for store orders
                order.createdAt()
        );
    }
}
