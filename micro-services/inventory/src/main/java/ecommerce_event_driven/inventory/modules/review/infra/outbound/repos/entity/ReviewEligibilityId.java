package ecommerce_event_driven.inventory.modules.review.infra.outbound.repos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chave composta de review_eligibility: (id_user, id_product, id_order). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEligibilityId implements Serializable {

    @Column(name = "id_user", nullable = false)
    private Long idUser;

    @Column(name = "id_product", nullable = false)
    private Long idProduct;

    @Column(name = "id_order", nullable = false)
    private Long idOrder;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReviewEligibilityId other)) {
            return false;
        }
        return Objects.equals(idUser, other.idUser)
                && Objects.equals(idProduct, other.idProduct)
                && Objects.equals(idOrder, other.idOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idUser, idProduct, idOrder);
    }
}
