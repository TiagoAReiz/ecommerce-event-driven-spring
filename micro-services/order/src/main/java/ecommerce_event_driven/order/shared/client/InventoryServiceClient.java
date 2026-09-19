package ecommerce_event_driven.order.shared.client;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ecommerce_event_driven.order.shared.web.ConflictException;
import ecommerce_event_driven.order.shared.web.BadRequestException;
import ecommerce_event_driven.order.shared.web.UnprocessableException;

/**
 * Cliente HTTP para o servico inventory com timeout de 3s.
 */
@Component
@ConfigurationProperties(prefix = "app.services.inventory")
public class InventoryServiceClient {
    private static final Logger LOG = LoggerFactory.getLogger(InventoryServiceClient.class);

    private String url;
    private ServiceTokenProvider serviceTokenProvider;

    /**
     * Toda chamada interna usa o token de servico (internal:hydrate). O token do usuario
     * que chegou na requisicao nao tem esse escopo e seria recusado com 403.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void setServiceTokenProvider(ServiceTokenProvider serviceTokenProvider) {
        this.serviceTokenProvider = serviceTokenProvider;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Valida produtos em lote: GET /internal/products?ids=1,2,3
     */
    public ProductsResponse getProducts(List<Long> productIds, String token) {
        if (productIds == null || productIds.isEmpty()) {
            return new ProductsResponse(List.of(), List.of());
        }

        String ids = String.join(",", productIds.stream().map(String::valueOf).toArray(String[]::new));

        try {
            var client = RestClient.builder()
                    .baseUrl(url)
                    .requestInterceptor((req, body, execution) -> {
                        req.getHeaders().add("Authorization", "Bearer " + serviceTokenProvider.getToken());
                        return execution.execute(req, body);
                    })
                    .build();

            var response = client.get()
                    .uri("/internal/products?ids={ids}", ids)
                    .retrieve()
                    .body(ProductsResponse.class);

            return response != null ? response : new ProductsResponse(List.of(), List.of());
        } catch (ResourceAccessException e) {
            LOG.error("Timeout ou erro de conexao com inventory: {}", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("timed out")) {
                throw new ecommerce_event_driven.order.shared.web.UpstreamException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "INVENTORY_TIMEOUT", "Timeout na validacao de produtos");
            }
            throw new ecommerce_event_driven.order.shared.web.UpstreamException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "INVENTORY_UNAVAILABLE", "Inventory indisponivel");
        } catch (HttpServerErrorException e) {
            LOG.error("Erro no servidor inventory: {}", e.getStatusCode());
            throw new ecommerce_event_driven.order.shared.web.UpstreamException(org.springframework.http.HttpStatus.BAD_GATEWAY, "INVENTORY_ERROR", "Erro no servidor inventory");
        } catch (HttpClientErrorException.NotFound e) {
            LOG.error("Produto nao encontrado no inventory");
            throw new BadRequestException("PRODUCT_NOT_FOUND", "Produto nao encontrado");
        } catch (Exception e) {
            LOG.error("Erro inesperado ao consultar inventory: {}", e.getMessage());
            throw new BadRequestException("SERVICE_RESPONSE_INVALID", "Resposta invalida do inventory");
        }
    }

    public record ProductsResponse(
            List<Product> products,
            List<Long> missing
    ) {}

    public record Product(
            Long id,
            String name,
            java.math.BigDecimal price,
            String photoUrl,
            Integer available,
            Boolean active
    ) {}
}
