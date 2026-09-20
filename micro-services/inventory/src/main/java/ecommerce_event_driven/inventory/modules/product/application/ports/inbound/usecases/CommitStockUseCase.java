package ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases;

import java.util.List;

public interface CommitStockUseCase {

    enum Result {
        COMMITTED,
        ALREADY_COMMITTED,
        COMMIT_FAILED
    }

    record Item(Long productId, Integer quantity) {}

    Result execute(Long idOrder, List<Item> items);
}
