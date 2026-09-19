package ecommerce_event_driven.order.shared.client;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ecommerce_event_driven.order.shared.web.BadRequestException;
import ecommerce_event_driven.order.shared.web.NotFoundException;

/**
 * Cliente HTTP para o servico user com timeout de 3s.
 */
@Component
@ConfigurationProperties(prefix = "app.services.user")
public class UserServiceClient {
    private static final Logger LOG = LoggerFactory.getLogger(UserServiceClient.class);

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
     * Obtem endereco do usuario: GET /internal/addresses/{id}?userId={userId}
     * Retorna endereco mesmo se foi soft-deleted.
     */
    public Address getAddress(Long addressId, Long userId, String token) {
        try {
            var client = RestClient.builder()
                    .baseUrl(url)
                    .requestInterceptor((req, body, execution) -> {
                        req.getHeaders().add("Authorization", "Bearer " + serviceTokenProvider.getToken());
                        return execution.execute(req, body);
                    })
                    .build();

            var response = client.get()
                    .uri("/internal/addresses/{id}?userId={userId}", addressId, userId)
                    .retrieve()
                    .body(Address.class);

            if (response == null) {
                throw new NotFoundException("Endereco nao encontrado");
            }
            return response;
        } catch (HttpClientErrorException.NotFound e) {
            LOG.error("Endereco {} nao encontrado para usuario {}", addressId, userId);
            throw new NotFoundException("Endereco nao encontrado");
        } catch (ResourceAccessException e) {
            LOG.error("Timeout ou erro de conexao com user: {}", e.getMessage());
            throw new ecommerce_event_driven.order.shared.web.UpstreamException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "USER_UNAVAILABLE", "Servico user indisponivel");
        } catch (HttpServerErrorException e) {
            LOG.error("Erro no servidor user: {}", e.getStatusCode());
            throw new ecommerce_event_driven.order.shared.web.UpstreamException(org.springframework.http.HttpStatus.BAD_GATEWAY, "USER_ERROR", "Erro no servidor user");
        } catch (Exception e) {
            LOG.error("Erro inesperado ao consultar endereco: {}", e.getMessage());
            throw new BadRequestException("SERVICE_RESPONSE_INVALID", "Resposta invalida do user");
        }
    }

    public record Address(
            Long id,
            Long userId,
            String street,
            String number,
            String complement,
            String city,
            String state,
            String zipcode,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean isDefault,
            Boolean removedAt
    ) {}
}
