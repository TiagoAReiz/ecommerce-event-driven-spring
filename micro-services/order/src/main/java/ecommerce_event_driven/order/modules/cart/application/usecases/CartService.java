package ecommerce_event_driven.order.modules.cart.application.usecases;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import ecommerce_event_driven.order.modules.cart.application.dtos.CartItemResponse;
import ecommerce_event_driven.order.modules.cart.application.dtos.CartResponse;
import ecommerce_event_driven.order.modules.cart.domain.models.Cart;
import ecommerce_event_driven.order.modules.cart.domain.models.CartItem;
import ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos.CartRepositoryPort;
import ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos.CartItemRepositoryPort;
import ecommerce_event_driven.order.shared.client.InventoryServiceClient;
import ecommerce_event_driven.order.shared.web.NotFoundException;
import ecommerce_event_driven.order.shared.web.UnprocessableException;
import ecommerce_event_driven.order.shared.web.BadRequestException;

/**
 * Logica de carrinho: adicionar, remover, listar e limpar.
 * Hidrata produtos do inventory e sinala problemas de disponibilidade.
 */
@Service
public class CartService {
    private static final int MAX_QUANTITY_PER_ITEM = 99;
    private static final int MAX_DISTINCT_ITEMS = 50;

    private final CartRepositoryPort cartRepo;
    private final CartItemRepositoryPort itemRepo;
    private final InventoryServiceClient inventoryClient;

    public CartService(CartRepositoryPort cartRepo, CartItemRepositoryPort itemRepo,
                       InventoryServiceClient inventoryClient) {
        this.cartRepo = cartRepo;
        this.itemRepo = itemRepo;
        this.inventoryClient = inventoryClient;
    }

    /**
     * Obtem o carrinho do usuario com hidratacao de produtos.
     */
    public CartResponse getCart(Long userId, String token) {
        var cart = cartRepo.findByIdUser(userId)
                .orElse(Cart.builder().idUser(userId).build());

        var items = itemRepo.findByIdCart(cart.id() != null ? cart.id() : 0L);

        if (items.isEmpty()) {
            return new CartResponse(List.of(), BigDecimal.ZERO, Instant.now());
        }

        // Obter dados dos produtos do inventory
        var productIds = items.stream().map(CartItem::idProduct).toList();
        var products = inventoryClient.getProducts(productIds, token);
        var productsById = products.products().stream()
                .collect(java.util.stream.Collectors.toMap(
                        InventoryServiceClient.Product::id,
                        p -> p
                ));

        // Montar resposta com hidratacao
        BigDecimal total = BigDecimal.ZERO;
        var responseItems = new java.util.ArrayList<CartItemResponse>();

        for (CartItem item : items) {
            var product = productsById.get(item.idProduct());
            var issues = new java.util.ArrayList<String>();

            if (product == null) {
                issues.add("PRODUCT_UNAVAILABLE");
            } else {
                if (!product.active()) {
                    issues.add("PRODUCT_UNAVAILABLE");
                }
                if (product.available() < item.quantity()) {
                    issues.add("INSUFFICIENT_STOCK");
                }
            }

            BigDecimal lineTotal = BigDecimal.ZERO;
            if (product != null && issues.isEmpty()) {
                lineTotal = product.price().multiply(BigDecimal.valueOf(item.quantity()));
                total = total.add(lineTotal);
            }

            var productInfo = product != null ?
                    new CartItemResponse.ProductInfo(
                            product.id(),
                            product.name(),
                            product.price(),
                            product.photoUrl(),
                            product.available() > 0,
                            product.active()
                    ) : null;

            responseItems.add(new CartItemResponse(
                    item.id(),
                    item.idProduct(),
                    item.quantity(),
                    productInfo,
                    lineTotal,
                    issues.isEmpty() ? List.of() : issues
            ));
        }

        return new CartResponse(responseItems, total, cart.updatedAt() != null ? cart.updatedAt() : Instant.now());
    }

    /**
     * Adiciona item ao carrinho (cria carrinho se nao existir, soma quantidade se ja existe).
     */
    public CartResponse addItem(Long userId, Long idProduct, Integer quantity, String token) {
        validateQuantity(quantity);

        // Validar que produto existe
        var products = inventoryClient.getProducts(List.of(idProduct), token);
        if (products.products().isEmpty()) {
            throw new NotFoundException("Produto nao encontrado");
        }

        // Criar ou obter carrinho
        var cart = cartRepo.findByIdUser(userId)
                .orElseGet(() -> cartRepo.save(
                        Cart.builder().idUser(userId).build()
                ));

        // Obter ou criar item
        var existingItem = itemRepo.findByIdCartAndIdProduct(cart.id(), idProduct);

        CartItem item;
        if (existingItem.isPresent()) {
            int newQty = existingItem.get().quantity() + quantity;
            if (newQty > MAX_QUANTITY_PER_ITEM) {
                throw new UnprocessableException("QUANTITY_EXCEEDS_LIMIT",
                        "Quantidade nao pode exceder " + MAX_QUANTITY_PER_ITEM);
            }
            item = existingItem.get().toBuilder().quantity(newQty).build();
        } else {
            // Verificar limite de linhas distintas
            var currentItems = itemRepo.findByIdCart(cart.id());
            if (currentItems.size() >= MAX_DISTINCT_ITEMS) {
                throw new BadRequestException("CART_LINE_LIMIT_EXCEEDED",
                        "Limite de linhas distintas atingido");
            }
            item = CartItem.builder()
                    .idCart(cart.id())
                    .idProduct(idProduct)
                    .quantity(quantity)
                    .build();
        }

        itemRepo.save(item);
        return getCart(userId, token);
    }

    /**
     * Atualiza a quantidade de um item (nao soma, define).
     */
    public CartResponse updateItem(Long userId, Long idProduct, Integer quantity, String token) {
        validateQuantity(quantity);

        var cart = cartRepo.findByIdUser(userId)
                .orElseThrow(() -> new NotFoundException("Carrinho nao encontrado"));

        var item = itemRepo.findByIdCartAndIdProduct(cart.id(), idProduct)
                .orElseThrow(() -> new NotFoundException("Item nao encontrado no carrinho"));

        // Revalidar estoque no inventory antes de atualizar
        var products = inventoryClient.getProducts(List.of(idProduct), token);
        if (products.products().isEmpty()) {
            throw new NotFoundException("Produto nao encontrado");
        }
        var product = products.products().get(0);
        if (quantity > product.available()) {
            throw new BadRequestException("INSUFFICIENT_STOCK",
                    "Estoque insuficiente para essa quantidade");
        }

        itemRepo.save(item.toBuilder().quantity(quantity).build());
        return getCart(userId, token);
    }

    /**
     * Remove um item do carrinho.
     */
    public CartResponse removeItem(Long userId, Long idProduct, String token) {
        var cart = cartRepo.findByIdUser(userId)
                .orElseThrow(() -> new NotFoundException("Carrinho nao encontrado"));

        var item = itemRepo.findByIdCartAndIdProduct(cart.id(), idProduct)
                .orElseThrow(() -> new NotFoundException("Item nao encontrado no carrinho"));

        itemRepo.save(item.toBuilder().deletedAt(Instant.now()).build());
        return getCart(userId, token);
    }

    /**
     * Limpa o carrinho (remove todos os itens).
     */
    public void clearCart(Long userId) {
        var cart = cartRepo.findByIdUser(userId);
        if (cart.isPresent()) {
            var items = itemRepo.findByIdCart(cart.get().id());
            for (CartItem item : items) {
                if (item.deletedAt() == null) {
                    itemRepo.save(item.toBuilder().deletedAt(Instant.now()).build());
                }
            }
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("INVALID_QUANTITY", "Quantidade deve ser >= 1");
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new UnprocessableException("QUANTITY_EXCEEDS_LIMIT",
                    "Quantidade nao pode exceder " + MAX_QUANTITY_PER_ITEM);
        }
    }
}
