package ecommerce_event_driven.order.modules.order.infra.outbound.repos;

import ecommerce_event_driven.order.modules.order.application.mappers.OrderItemMapper;
import ecommerce_event_driven.order.modules.order.application.mappers.OrderMapper;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderItem;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity.OrderEntity;
import ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity.OrderItemEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;
    private final OrderItemJpaRepository itemJpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository jpaRepository,
            OrderItemJpaRepository itemJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.itemJpaRepository = itemJpaRepository;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        OrderEntity saved = jpaRepository.save(OrderMapper.toEntity(order));

        List<OrderItemEntity> toSave = order.items().stream()
                .map(item -> OrderItemMapper.toEntity(item, saved))
                .toList();
        List<OrderItem> savedItems = itemJpaRepository.saveAll(toSave).stream()
                .map(OrderItemMapper::toDomain)
                .toList();

        return OrderMapper.toDomain(saved, savedItems);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id).map(this::withItems);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdAndIdCustomer(Long id, Long idCustomer) {
        return jpaRepository.findByIdAndIdCustomer(id, idCustomer).map(this::withItems);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> findByIdCustomer(Long idCustomer, Pageable pageable) {
        return withItems(jpaRepository.findByIdCustomer(idCustomer, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> findByStatus(OrderStatus status, Pageable pageable) {
        return withItems(jpaRepository.findByStatus(status, pageable));
    }

    @Override
    @Transactional
    public boolean updateStatus(Long idOrder, OrderStatus from, OrderStatus to) {
        return jpaRepository.updateStatus(idOrder, from, to) == 1;
    }

    private Order withItems(OrderEntity entity) {
        List<OrderItem> items = itemJpaRepository.findByOrderId(entity.getId()).stream()
                .map(OrderItemMapper::toDomain)
                .toList();
        return OrderMapper.toDomain(entity, items);
    }

    /** Duas queries por pagina, em vez de uma por pedido. */
    private Page<Order> withItems(Page<OrderEntity> page) {
        List<Long> ids = page.getContent().stream().map(OrderEntity::getId).toList();
        if (ids.isEmpty()) {
            return page.map(entity -> OrderMapper.toDomain(entity, List.of()));
        }

        Map<Long, List<OrderItem>> itemsByOrder = itemJpaRepository.findByOrderIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        // Ler so o id do proxy LAZY nao dispara carga do pedido.
                        item -> item.getOrder().getId(),
                        Collectors.mapping(OrderItemMapper::toDomain, Collectors.toList())));

        return page.map(entity ->
                OrderMapper.toDomain(entity, itemsByOrder.getOrDefault(entity.getId(), List.of())));
    }
}
