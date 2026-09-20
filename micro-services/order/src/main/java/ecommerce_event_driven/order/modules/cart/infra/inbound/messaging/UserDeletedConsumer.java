package ecommerce_event_driven.order.modules.cart.infra.inbound.messaging;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.cart.application.usecases.UserDeletedEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de user.deleted no modulo cart.
 * Executa soft delete de carrinho e itens do usuario.
 */
@Component
public class UserDeletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserDeletedConsumer.class);

    private final UserDeletedEventHandler handler;

    public UserDeletedConsumer(UserDeletedEventHandler handler) {
        this.handler = handler;
    }

    /**
     * Usuario foi deletado: soft delete de seu carrinho.
     */
    @KafkaListener(topics = "ecommerce.user.deleted.v1", groupId = "order")
    public void onUserDeleted(UserDeletedEventDto event) throws InvalidEventException {
        log.debug("user.deleted recebido para userId {}", event.userId());
        handler.handle(event.userId(), event.deletedAt());
    }

    record UserDeletedEventDto(
            String eventId,
            java.time.Instant producedAt,
            Long userId,
            java.time.Instant deletedAt) {
    }
}
