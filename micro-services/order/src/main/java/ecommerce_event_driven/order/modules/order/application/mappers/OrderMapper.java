package ecommerce_event_driven.order.modules.order.application.mappers;

import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderItem;
import ecommerce_event_driven.order.modules.order.infra.outbound.repos.entity.OrderEntity;
import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    /**
     * Os itens vem separados porque OrderEntity nao mapeia a colecao: quem
     * monta o agregado e o adapter, que controla como os itens sao carregados.
     */
    public static Order toDomain(OrderEntity entity, List<OrderItem> items) {
        if (entity == null) {
            return null;
        }
        return Order.builder()
                .id(entity.getId())
                .idCustomer(entity.getIdCustomer())
                .idAddress(entity.getIdAddress())
                .status(entity.getStatus())
                .items(items)
                .itemsCost(entity.getItemsCost())
                .freightCost(entity.getFreightCost())
                .totalCost(entity.getTotalCost())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static OrderEntity toEntity(Order model) {
        if (model == null) {
            return null;
        }
        return OrderEntity.builder()
                .id(model.id())
                .idCustomer(model.idCustomer())
                .idAddress(model.idAddress())
                .status(model.status())
                .itemsCost(model.itemsCost())
                .freightCost(model.freightCost())
                .totalCost(model.totalCost())
                .createdAt(model.createdAt())
                .updatedAt(model.updatedAt())
                .build();
    }
}
