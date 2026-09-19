package ecommerce_event_driven.order.modules.order.application.usecases;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos.CartRepositoryPort;
import ecommerce_event_driven.order.modules.cart.application.ports.outbound.repos.CartItemRepositoryPort;
import ecommerce_event_driven.order.modules.order.application.dtos.CreateOrderRequest;
import ecommerce_event_driven.order.modules.order.application.dtos.CreateOrderResponse;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderItem;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import ecommerce_event_driven.order.shared.client.InventoryServiceClient;
import ecommerce_event_driven.order.shared.client.UserServiceClient;
import ecommerce_event_driven.order.shared.client.ShipmentServiceClient;
import ecommerce_event_driven.order.shared.web.BadRequestException;
import ecommerce_event_driven.order.shared.web.ConflictException;
import ecommerce_event_driven.order.shared.web.UnprocessableException;

/**
 * Servico de checkout que valida com inventory, user e shipment.
 * As validacoes sao feitas ANTES de abrir a transacao.
 */
@Service
public class CheckoutService {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutService.class);

    private final CheckoutTransaction checkoutTransaction;
    private final OrderRepositoryPort orderRepo;
    private final CartRepositoryPort cartRepo;
    private final CartItemRepositoryPort cartItemRepo;
    private final InventoryServiceClient inventoryClient;
    private final UserServiceClient userClient;
    private final ShipmentServiceClient shipmentClient;

    public CheckoutService(CheckoutTransaction checkoutTransaction, OrderRepositoryPort orderRepo,
                           CartRepositoryPort cartRepo, CartItemRepositoryPort cartItemRepo,
                           InventoryServiceClient inventoryClient, UserServiceClient userClient,
                           ShipmentServiceClient shipmentClient) {
        this.checkoutTransaction = checkoutTransaction;
        this.orderRepo = orderRepo;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.inventoryClient = inventoryClient;
        this.userClient = userClient;
        this.shipmentClient = shipmentClient;
    }

    /**
     * Executa checkout com todas as validacoes upstream.
     * As tres chamadas HTTP acontecem ANTES da transacao.
     */
    public CreateOrderResponse checkout(Long userId, CreateOrderRequest request, String token) {
        // 1. Validar carrinho nao vazio
        var cart = cartRepo.findByIdUser(userId)
                .orElseThrow(() -> new UnprocessableException("EMPTY_CART", "Carrinho vazio"));
        
        var cartItems = cartItemRepo.findByIdCart(cart.id());
        if (cartItems.isEmpty()) {
            throw new UnprocessableException("EMPTY_CART", "Carrinho vazio");
        }

        // 2. Validar produtos no inventory
        var productIds = cartItems.stream().map(ci -> ci.idProduct()).collect(Collectors.toList());
        var inventoryResponse = inventoryClient.getProducts(productIds, token);
        var productsById = inventoryResponse.products().stream()
                .collect(Collectors.toMap(InventoryServiceClient.Product::id, p -> p));

        // Validar que todos os produtos existem, sao ativos e tem estoque suficiente
        for (var cartItem : cartItems) {
            var product = productsById.get(cartItem.idProduct());
            if (product == null) {
                throw new ConflictException("PRODUCT_NOT_FOUND", 
                    "Produto " + cartItem.idProduct() + " nao encontrado");
            }
            if (!product.active()) {
                throw new UnprocessableException("PRODUCT_UNAVAILABLE",
                    "Produto " + product.id() + " nao esta disponivel");
            }
            if (product.available() < cartItem.quantity()) {
                throw new ConflictException("INSUFFICIENT_STOCK",
                    "Estoque insuficiente para " + product.id());
            }
        }

        // 3. Validar endereco no user service
        var address = userClient.getAddress(request.addressId(), userId, token);
        if (address == null) {
            throw new ConflictException("ADDRESS_NOT_FOUND", "Endereco nao encontrado");
        }

        // 4. Calcular frete no shipment
        var quote = shipmentClient.getQuote(address.zipcode(), token);
        if (quote == null || quote.freightCost() == null) {
            throw new ConflictException("SHIPPING_QUOTE_ERROR", "Nao foi possivel calcular o frete");
        }

        // 5. Calcular custos
        BigDecimal itemsCost = BigDecimal.ZERO;
        List<OrderItem> orderItems = new java.util.ArrayList<>();
        
        for (var cartItem : cartItems) {
            var product = productsById.get(cartItem.idProduct());
            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(cartItem.quantity()));
            itemsCost = itemsCost.add(lineTotal);
            
            OrderItem orderItem = OrderItem.builder()
                    .idProduct(cartItem.idProduct())
                    .productName(product.name())
                    .productPhotoUrl(product.photoUrl())
                    .priceAtTime(product.price())
                    .quantity(cartItem.quantity())
                    .build();
            orderItems.add(orderItem);
        }

        BigDecimal totalCost = itemsCost.add(quote.freightCost());

        // 6. Validar expectedTotalCost se fornecido
        if (request.expectedTotalCost() != null) {
            if (!request.expectedTotalCost().equals(totalCost)) {
                throw new ConflictException("PRICE_CHANGED",
                    "Preco total mudou. Esperado: " + request.expectedTotalCost() + 
                    ", Atual: " + totalCost);
            }
        }

        // 7. Criar pedido
        var order = Order.builder()
                .idCustomer(userId)
                .idAddress(request.addressId())
                .status(OrderStatus.pending)
                .items(orderItems)
                .itemsCost(itemsCost)
                .freightCost(quote.freightCost())
                .totalCost(totalCost)
                .stockReservation("pending")
                .build();

        // 8. Executar checkout (transacao)
        var saved = checkoutTransaction.executeCheckout(order, userId);

        // 9. Montar response com items
        List<CreateOrderResponse.OrderItemResponse> responseItems = saved.items().stream()
                .map(item -> new CreateOrderResponse.OrderItemResponse(
                    item.id(),
                    item.idProduct(),
                    item.productName(),
                    item.productPhotoUrl(),
                    item.priceAtTime(),
                    item.quantity(),
                    item.priceAtTime().multiply(BigDecimal.valueOf(item.quantity()))
                ))
                .toList();

        return new CreateOrderResponse(
                saved.id(),
                saved.status().name(),
                saved.idAddress(),
                responseItems,
                saved.itemsCost(),
                saved.freightCost(),
                saved.totalCost(),
                saved.stockReservation(),
                new CreateOrderResponse.NextStep("CREATE_PAYMENT", "/api/v1/payments"),
                saved.createdAt()
        );
    }
}
