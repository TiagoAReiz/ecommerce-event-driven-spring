package ecommerce_event_driven.inventory.modules.product.domain.exceptions;

/**
 * Lancada quando nao ha disponivel (stock - reserved) para segurar o item.
 *
 * <p>E de proposito que isso seja excecao e nao retorno: ela derruba a
 * transacao, e o rollback desfaz as reservas dos itens que ja tinham passado.
 * Pedido reserva tudo ou nada, sem logica de compensacao escrita a mao.
 */
public class InsufficientStockException extends RuntimeException {

    private final Long idOrder;
    private final Long idProduct;

    public InsufficientStockException(Long idOrder, Long idProduct, int requested) {
        super("Estoque insuficiente para o produto " + idProduct
                + " (pedido " + idOrder + ", quantidade " + requested + ")");
        this.idOrder = idOrder;
        this.idProduct = idProduct;
    }

    public Long getIdOrder() {
        return idOrder;
    }

    public Long getIdProduct() {
        return idProduct;
    }
}
