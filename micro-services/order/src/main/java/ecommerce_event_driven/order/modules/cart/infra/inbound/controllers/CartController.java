package ecommerce_event_driven.order.modules.cart.infra.inbound.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import ecommerce_event_driven.order.modules.cart.application.dtos.CartItemRequest;
import ecommerce_event_driven.order.modules.cart.application.dtos.UpdateCartItemRequest;
import ecommerce_event_driven.order.modules.cart.application.dtos.CartResponse;
import ecommerce_event_driven.order.modules.cart.application.usecases.CartService;
import ecommerce_event_driven.order.shared.security.CurrentUser;

/**
 * Controlador para operacoes de carrinho.
 * Protegido com escopos de autorizacao: cart:read e cart:write.
 */
@RestController
@RequestMapping("/cart")
public class CartController {
    private final CartService cartService;
    private final CurrentUser currentUser;

    public CartController(CartService cartService, CurrentUser currentUser) {
        this.cartService = cartService;
        this.currentUser = currentUser;
    }

    /**
     * GET /cart - Obtem o carrinho com hidratacao de produtos.
     */
    @GetMapping
    public ResponseEntity<CartResponse> getCart(JwtAuthenticationToken token) {
        Long userId = currentUser.getId(token.getToken());
        String accessToken = token.getToken().getTokenValue();
        CartResponse cart = cartService.getCart(userId, accessToken);
        return ResponseEntity.ok(cart);
    }

    /**
     * POST /cart/items - Adiciona item ao carrinho ou soma quantidade.
     */
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @Valid @RequestBody CartItemRequest request,
            JwtAuthenticationToken token) {
        Long userId = currentUser.getId(token.getToken());
        String accessToken = token.getToken().getTokenValue();
        CartResponse cart = cartService.addItem(userId, request.idProduct(), request.quantity(), accessToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    /**
     * PUT /cart/items/{idProduct} - Define a quantidade de um item (nao soma).
     */
    @PutMapping("/items/{idProduct}")
    public ResponseEntity<CartResponse> updateItem(
            @PathVariable Long idProduct,
            @Valid @RequestBody UpdateCartItemRequest request,
            JwtAuthenticationToken token) {
        Long userId = currentUser.getId(token.getToken());
        String accessToken = token.getToken().getTokenValue();
        CartResponse cart = cartService.updateItem(userId, idProduct, request.quantity(), accessToken);
        return ResponseEntity.ok(cart);
    }

    /**
     * DELETE /cart/items/{idProduct} - Remove um item do carrinho.
     */
    @DeleteMapping("/items/{idProduct}")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable Long idProduct,
            JwtAuthenticationToken token) {
        Long userId = currentUser.getId(token.getToken());
        String accessToken = token.getToken().getTokenValue();
        CartResponse cart = cartService.removeItem(userId, idProduct, accessToken);
        return ResponseEntity.ok(cart);
    }

    /**
     * DELETE /cart - Limpa o carrinho (remove todos os itens).
     */
    @DeleteMapping
    public ResponseEntity<Void> clearCart(JwtAuthenticationToken token) {
        Long userId = currentUser.getId(token.getToken());
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
