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
 * Lista os pedidos do usuario com filtros opcionais.
 */
@Service
public class ListMyOrdersUseCase {
    private final OrderRepositoryPort orderRepo;

    public ListMyOrdersUseCase(OrderRepositoryPort orderRepo) {
        this.orderRepo = orderRepo;
    }

    /**
     * Lista pedidos do usuario com filtros.
     * @param idCustomer ID do usuario
     * @param statuses Filtros de status (opcional)
     * @param from Data de inicio (inclusive)
     * @param to Data de fim (inclusive)
     * @param page Numero da pagina (0-indexed)
     * @param size Tamanho da pagina
     * @param sort Campo e direcao (ex: "createdAt,desc")
     * @return Pagina de pedidos
     */
    public OrderListResponse execute(Long idCustomer, List<OrderStatus> statuses, LocalDate from, LocalDate to,
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

        // Query database
        Page<Order> result = orderRepo.findByIdCustomer(idCustomer, pageable);

        // Filter by status if provided
        List<Order> filtered = result.getContent();
        if (statuses != null && !statuses.isEmpty()) {
            filtered = filtered.stream()
                    .filter(o -> statuses.contains(o.status()))
                    .toList();
        }

        // Filter by date range if provided
        if (from != null || to != null) {
            Instant fromInstant = from != null ? from.atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.EPOCH;
            Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() : Instant.now();

            filtered = filtered.stream()
                    .filter(o -> o.createdAt() != null && o.createdAt().isAfter(fromInstant) && o.createdAt().isBefore(toInstant))
                    .toList();
        }

        // Map to response
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
                null,  // idCustomer is null for user's own orders
                order.createdAt()
        );
    }
}
