package ecommerce_event_driven.user.modules.owner.application.usecases;

import ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases.ProvisionStoreOwnerPort;
import ecommerce_event_driven.user.modules.owner.application.ports.outbound.repos.OwnerRepositoryPort;
import ecommerce_event_driven.user.modules.owner.domain.models.Owner;
import ecommerce_event_driven.user.modules.user.application.ports.outbound.repos.UserRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Loja unica: existe um so owner, e quem ele e vem da configuracao.
 *
 * <p>Nao e seed por migration porque owner.id_user tem FK para users, e o
 * usuario da loja so passa a existir depois do primeiro login com o Google.
 */
@Service
public class ProvisionStoreOwner implements ProvisionStoreOwnerPort {

    private static final Logger log = LoggerFactory.getLogger(ProvisionStoreOwner.class);

    private final OwnerRepositoryPort owners;
    private final UserRepositoryPort users;
    private final String storeOwnerEmail;

    public ProvisionStoreOwner(
            OwnerRepositoryPort owners,
            UserRepositoryPort users,
            @Value("${app.store.owner-email}") String storeOwnerEmail) {
        // Loja sem dono nao tem quem cadastre produto nem despache pedido.
        // Melhor nao subir do que subir sem ninguem capaz de operar a loja.
        if (!StringUtils.hasText(storeOwnerEmail)) {
            throw new IllegalStateException("app.store.owner-email vazio: a loja precisa de um dono");
        }
        this.owners = owners;
        this.users = users;
        this.storeOwnerEmail = storeOwnerEmail.trim();
    }

    /**
     * Roda dentro da transacao do CreateUser: se a criacao do owner falhar, o
     * usuario tambem nao e criado, e o proximo login tenta tudo de novo.
     */
    @Override
    @Transactional
    public void provisionIfStoreOwner(Long idUser, String email) {
        // Case-insensitive como o indice users_email_uk, que e sobre lower(email).
        if (storeOwnerEmail.equalsIgnoreCase(email)) {
            assign(idUser);
        }
    }

    @Override
    @Transactional
    public void provisionAtStartup() {
        users.findByEmail(storeOwnerEmail).ifPresentOrElse(
                user -> assign(user.id()),
                () -> log.info("Dono da loja ({}) ainda nao fez login: vira owner no primeiro acesso",
                        storeOwnerEmail));
    }

    /**
     * Idempotente: se o usuario ja e o dono, nao faz nada.
     *
     * <p>Se o dono ativo e outro usuario, a configuracao mudou de proposito, e
     * quem controla a configuracao ja controla a loja. O dono antigo e desativado
     * antes do novo entrar, porque owner_single_uk so admite um ativo.
     */
    private void assign(Long idUser) {
        Optional<Owner> current = owners.findActive();

        if (current.isPresent()) {
            Owner owner = current.get();
            if (owner.idUser().equals(idUser)) {
                return;
            }
            log.warn("Dono da loja trocado pela configuracao: user {} sai, user {} entra",
                    owner.idUser(), idUser);
            owners.save(owner.toBuilder().deletedAt(Instant.now()).build());
        }

        owners.save(Owner.builder().idUser(idUser).build());
        log.info("User {} provisionado como dono da loja", idUser);
    }
}
