package ecommerce_event_driven.api_gateway.modules.proxy;

import ecommerce_event_driven.api_gateway.shared.web.NotFoundException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mapeia o primeiro segmento da rota /api/v1/** para o servico downstream.
 *
 * Exemplo:
 * /api/v1/products/123 -> inventory:8082
 * /api/v1/users/42 -> user:8081
 * /api/v1/orders/1 -> order:8083
 */
@Component
public class RouteTable {

    private final String userServiceUrl;
    private final String inventoryServiceUrl;
    private final String orderServiceUrl;
    private final String paymentServiceUrl;
    private final String shipmentServiceUrl;

    public RouteTable(
            @Value("${app.services.user.url}") String userServiceUrl,
            @Value("${app.services.inventory.url}") String inventoryServiceUrl,
            @Value("${app.services.order.url}") String orderServiceUrl,
            @Value("${app.services.payment.url}") String paymentServiceUrl,
            @Value("${app.services.shipment.url}") String shipmentServiceUrl) {
        this.userServiceUrl = userServiceUrl;
        this.inventoryServiceUrl = inventoryServiceUrl;
        this.orderServiceUrl = orderServiceUrl;
        this.paymentServiceUrl = paymentServiceUrl;
        this.shipmentServiceUrl = shipmentServiceUrl;
    }

    /**
     * Resolve o URL base do servico dado o primeiro segmento da rota.
     * Exs: "users" -> userServiceUrl, "products" -> inventoryServiceUrl.
     *
     * @param pathSegment primeiro segmento apos /api/v1
     * @return URL base do servico
     * @throws NotFoundException se o segmento nao for reconhecido ou for /internal
     */
    public String resolveServiceUrl(String pathSegment) {
        if (pathSegment == null || pathSegment.isEmpty()) {
            throw new NotFoundException("Not found", "NOT_FOUND");
        }

        // Bloqueia acesso a /api/v1/internal/**
        if (pathSegment.equals("internal")) {
            throw new NotFoundException("Not found", "NOT_FOUND");
        }

        return switch (pathSegment) {
            case "users" -> userServiceUrl;
            case "products", "categories", "reviews" -> inventoryServiceUrl;
            case "cart", "orders" -> orderServiceUrl;
            case "payments" -> paymentServiceUrl;
            case "shipments", "shipping" -> shipmentServiceUrl;
            default -> throw new NotFoundException("Not found", "NOT_FOUND");
        };
    }

    /**
     * Resolve o timeout de leitura em millisegundos para o servico.
     * Payment tem timeout maior.
     */
    public int readTimeoutMillis(String pathSegment) {
        return "payments".equals(pathSegment) ? 15000 : 5000;
    }

    /**
     * Resolve o timeout de conexao em millisegundos.
     */
    public int connectTimeoutMillis() {
        return 2000;
    }
}
