package ecommerce_event_driven.user.modules.owner.application.ports.inbound.usecases;

/**
 * Garante que a conta configurada em app.store.owner-email e o dono da loja.
 *
 * <p>O owner nao nasce pela API: ele e consequencia da configuracao. Os dois
 * metodos cobrem os dois momentos em que o usuario da loja pode aparecer.
 */
public interface ProvisionStoreOwnerPort {

    /** Chamado quando um usuario e criado: se o e-mail e o da loja, ele vira o dono. */
    void provisionIfStoreOwner(Long idUser, String email);

    /** Chamado no boot: cobre o usuario que ja existia antes de a configuracao apontar para ele. */
    void provisionAtStartup();
}
