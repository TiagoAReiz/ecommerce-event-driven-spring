package ecommerce_event_driven.user.modules.user.infra.outbound.repos;

import ecommerce_event_driven.user.modules.user.infra.outbound.repos.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Escrita casada com users_email_uk, que e um indice sobre lower(email)
     * restrito a deleted_at IS NULL. IgnoreCase geraria upper(...) e nao usaria
     * o indice.
     */
    @Query("""
            select u from UserEntity u
            where lower(u.email) = lower(:email)
              and u.deletedAt is null
            """)
    Optional<UserEntity> findActiveByEmail(@Param("email") String email);

    Optional<UserEntity> findByGoogleSubAndDeletedAtIsNull(String googleSub);

    Optional<UserEntity> findByCpfAndDeletedAtIsNull(String cpf);

    Optional<UserEntity> findByIdAndDeletedAtIsNull(Long id);
}
