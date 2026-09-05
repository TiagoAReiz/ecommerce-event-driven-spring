package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Preenchida por evento OrderDelivered vindo do order.
 * Sem isso o inventory nao tem como provar que a compra existiu.
 */
@Entity
@Table(name = "review_eligibility")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewEligibilityEntity {

    @EmbeddedId
    private ReviewEligibilityId id;

    @Generated(event = EventType.INSERT)
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
}
