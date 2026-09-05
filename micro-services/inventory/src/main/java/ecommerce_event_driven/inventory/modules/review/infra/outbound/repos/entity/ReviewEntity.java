package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Servico user, sem FK. */
    @Column(name = "id_user", nullable = false)
    private Long idUser;

    /** [snapshot] */
    @Column(name = "user_name", length = 150)
    private String userName;

    /** [snapshot] */
    @Column(name = "user_photo_url")
    private String userPhotoUrl;

    /** FK para product(id). Mantida como id puro: outro modulo, outra fronteira. */
    @Column(name = "id_product", nullable = false)
    private Long idProduct;

    /** Servico order, sem FK. */
    @Column(name = "id_order", nullable = false)
    private Long idOrder;

    @Column(name = "rate", nullable = false)
    private Short rate;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "description")
    private String description;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
