package ecommerce_event_driven.order.modules.cart.application.usecases;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import java.sql.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trata o evento user.deleted: soft delete de cart e cart_items do usuario.
 */
@Component
public class UserDeletedEventHandler {
    private static final Logger log = LoggerFactory.getLogger(UserDeletedEventHandler.class);

    private final JdbcTemplate jdbcTemplate;

    public UserDeletedEventHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void handle(Long userId, Instant deletedAt) throws InvalidEventException {
        if (userId == null || userId <= 0) {
            throw new InvalidEventException("userId invalido: " + userId);
        }

        Timestamp deletedAtTs = Timestamp.from(deletedAt);

        // Soft delete de cart_items do usuario (via cart)
        int itemsDeleted = jdbcTemplate.update(
                "UPDATE cart_items SET deleted_at = ? WHERE id_cart IN " +
                "(SELECT id FROM cart WHERE id_user = ? AND deleted_at IS NULL) " +
                "AND deleted_at IS NULL",
                deletedAtTs, userId
        );

        // Soft delete de cart do usuario
        int cartsDeleted = jdbcTemplate.update(
                "UPDATE cart SET deleted_at = ? WHERE id_user = ? AND deleted_at IS NULL",
                deletedAtTs, userId
        );

        log.info("user.deleted executado para userId {}: {} carrinhos, {} itens marcados como deletados",
                userId, cartsDeleted, itemsDeleted);
    }
}
