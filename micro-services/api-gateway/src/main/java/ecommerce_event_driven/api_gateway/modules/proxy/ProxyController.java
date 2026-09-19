package ecommerce_event_driven.api_gateway.modules.proxy;

import ecommerce_event_driven.api_gateway.modules.auth.application.ScopePolicy;
import ecommerce_event_driven.api_gateway.modules.auth.application.dtos.IssuedToken;
import ecommerce_event_driven.api_gateway.modules.auth.infra.outbound.cache.ProfileCache;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.external.UserMicroservicePort;
import ecommerce_event_driven.api_gateway.modules.auth.application.ports.outbound.security.TokenIssuerPort;
import ecommerce_event_driven.api_gateway.shared.web.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Proxy que roteia /api/v1/** para os microsservicos.
 *
 * Troca o token do browser (aud=front) por um token interno (aud=internal)
 * com os escopos do papel do usuario.
 */
@RestController
@RequestMapping("/api/v1/**")
public class ProxyController {

    private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);

    private final RouteTable routeTable;
    private final TokenIssuerPort tokenIssuer;
    private final ScopePolicy scopePolicy;
    private final ProfileCache profileCache;
    private final UserMicroservicePort userMicroservice;

    public ProxyController(
            RouteTable routeTable,
            TokenIssuerPort tokenIssuer,
            ScopePolicy scopePolicy,
            ProfileCache profileCache,
            UserMicroservicePort userMicroservice) {
        this.routeTable = routeTable;
        this.tokenIssuer = tokenIssuer;
        this.scopePolicy = scopePolicy;
        this.profileCache = profileCache;
        this.userMicroservice = userMicroservice;
    }

    @RequestMapping
    public ResponseEntity<?> proxy(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt token) {

        // Extrai o primeiro segmento da rota para resolver o servico
        String pathInfo = request.getRequestURI().substring("/api/v1".length());
        String firstSegment = pathInfo.substring(1).split("/")[0]; // Remove / inicial e pega primeiro

        // Resolve o URL do servico (pode lancar NotFoundException)
        String serviceUrl = routeTable.resolveServiceUrl(firstSegment);

        // Emite token interno
        IssuedToken internalToken;
        if (token != null) {
            // Usuario autenticado: emite token com seus escopos
            Long userId = Long.parseLong(token.getSubject());
            var cached = profileCache.get(userId)
                    .orElseGet(() -> userMicroservice.findProfile(userId).orElse(null));

            if (cached != null) {
                profileCache.set(cached);
                Set<String> scopes = scopePolicy.scopesForRoles(cached.roles());
                internalToken = tokenIssuer.issueInternal(
                        String.valueOf(userId),
                        cached.roles(),
                        scopes);
            } else {
                // Token valido de usuario que nao existe mais: sessao invalida, nao pedido mal formado.
                return ResponseEntity.status(401).build();
            }
        } else {
            // Sem autenticacao: emite token de anonimo
            Set<String> scopes = scopePolicy.scopesForAnonymous();
            internalToken = tokenIssuer.issueInternal("anonymous", List.of(), scopes);
        }

        // Repassa ao servico
        // getRequestURI nao traz a query string; sem ela, filtros e buscas chegariam vazios.
        String query = request.getQueryString();
        String target = serviceUrl + pathInfo + (query != null ? "?" + query : "");
        return proxyRequest(serviceUrl, request, target, internalToken);
    }

    private ResponseEntity<?> proxyRequest(
            String serviceUrl,
            HttpServletRequest request,
            String pathInfo,
            IssuedToken token) {
        try {
            RestClient client = buildRestClient(serviceUrl, request.getMethod().split("/")[0]);

            // Lê o corpo inteiro para repassar (bytes)
            byte[] requestBody = request.getInputStream().readAllBytes();

            // Extrai headers para repassar
            String contentType = request.getContentType();
            String accept = request.getHeader(HttpHeaders.ACCEPT);
            String idempotencyKey = request.getHeader("Idempotency-Key");
            String ifNoneMatch = request.getHeader("If-None-Match");
            String ifMatch = request.getHeader("If-Match");
            String requestId = request.getHeader("X-Request-Id");

            var spec = client.method(org.springframework.http.HttpMethod.valueOf(request.getMethod()))
                    .uri(java.net.URI.create(pathInfo))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.value())
                    .header("X-Request-Id", requestId != null ? requestId : "");

            if (contentType != null && !contentType.isEmpty()) {
                spec = spec.header(HttpHeaders.CONTENT_TYPE, contentType);
            }
            if (accept != null && !accept.isEmpty()) {
                spec = spec.header(HttpHeaders.ACCEPT, accept);
            }
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                spec = spec.header("Idempotency-Key", idempotencyKey);
            }
            if (ifNoneMatch != null && !ifNoneMatch.isEmpty()) {
                spec = spec.header("If-None-Match", ifNoneMatch);
            }
            if (ifMatch != null && !ifMatch.isEmpty()) {
                spec = spec.header("If-Match", ifMatch);
            }

            if (requestBody.length > 0) {
                spec = spec.body(requestBody);
            }

            return spec.exchange((req, res) -> {
                // Lê resposta
                byte[] responseBody = res.getBody().readAllBytes();
                var response = ResponseEntity.status(res.getStatusCode());

                // Headers de resposta
                String resContentType = res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                if (resContentType != null) {
                    response = response.header(HttpHeaders.CONTENT_TYPE, resContentType);
                }

                String location = res.getHeaders().getFirst(HttpHeaders.LOCATION);
                if (location != null) {
                    // Reescreve Location com prefixo /api/v1
                    // O servico pode devolver URL absoluta com o host interno; so o caminho
                    // e a query interessam ao cliente, sempre sob o prefixo publico.
                    java.net.URI loc = java.net.URI.create(location);
                    String publicPath = "/api/v1" + loc.getRawPath()
                            + (loc.getRawQuery() != null ? "?" + loc.getRawQuery() : "");
                    response = response.header(HttpHeaders.LOCATION, publicPath);
                }

                String etag = res.getHeaders().getFirst("ETag");
                if (etag != null) {
                    response = response.header("ETag", etag);
                }

                String cacheControl = res.getHeaders().getFirst("Cache-Control");
                if (cacheControl != null) {
                    response = response.header("Cache-Control", cacheControl);
                }

                String retryAfter = res.getHeaders().getFirst("Retry-After");
                if (retryAfter != null) {
                    response = response.header("Retry-After", retryAfter);
                }

                String idempotencyReplayed = res.getHeaders().getFirst("Idempotency-Replayed");
                if (idempotencyReplayed != null) {
                    response = response.header("Idempotency-Replayed", idempotencyReplayed);
                }

                return response.body(responseBody);
            });

        } catch (ResourceAccessException e) {
            logger.warn("Service unreachable or timeout: {}", e.getMessage());
            if (e.getMessage().contains("timeout")) {
                return gatewayTimeout();
            } else {
                return serviceUnavailable();
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.warn("Downstream error: {} {}", e.getStatusCode(), e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("Proxy error", e);
            return internalServerError();
        }
    }

    private RestClient buildRestClient(String baseUrl, String pathSegment) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(routeTable.connectTimeoutMillis());
        factory.setReadTimeout(routeTable.readTimeoutMillis(pathSegment));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    private ResponseEntity<ProblemDetail> serviceUnavailable() {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(503),
                "Service temporarily unavailable");
        detail.setProperty("code", "SERVICE_UNAVAILABLE");
        detail.setProperty("timestamp", java.time.Instant.now());
        return ResponseEntity.status(503).body(detail);
    }

    private ResponseEntity<ProblemDetail> gatewayTimeout() {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(504),
                "Gateway timeout");
        detail.setProperty("code", "GATEWAY_TIMEOUT");
        detail.setProperty("timestamp", java.time.Instant.now());
        return ResponseEntity.status(504).body(detail);
    }

    private ResponseEntity<ProblemDetail> internalServerError() {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(500),
                "Internal server error");
        detail.setProperty("code", "INTERNAL_SERVER_ERROR");
        detail.setProperty("timestamp", java.time.Instant.now());
        return ResponseEntity.status(500).body(detail);
    }
}
