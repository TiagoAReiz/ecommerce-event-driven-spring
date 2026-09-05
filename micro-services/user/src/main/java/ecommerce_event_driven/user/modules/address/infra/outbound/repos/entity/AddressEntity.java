package ecommerce_event_driven.user.modules.address.infra.outbound.repos.entity;

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
@Table(name = "address")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** FK para users(id). Mantida como id puro: outro modulo, outra fronteira. */
    @Column(name = "id_user", nullable = false)
    private Long idUser;

    /** Apelido do endereco: "casa", "trabalho". */
    @Column(name = "name", length = 60)
    private String name;

    @Column(name = "zipcode", nullable = false, length = 20)
    private String zipcode;

    @Column(name = "country", nullable = false, length = 60)
    private String country;

    @Column(name = "state", nullable = false, length = 60)
    private String state;

    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @Column(name = "street", nullable = false, length = 200)
    private String street;

    @Column(name = "number", length = 20)
    private String number;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
