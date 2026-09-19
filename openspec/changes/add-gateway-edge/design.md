# Design

## Context

Código atual em `micro-services/api-gateway`: `config/SecurityConfig` (oauth2Login com
`AuthSuccessHandler`), `config/JwtKeyConfig` (bean `RSAKey` e `JwtEncoder`),
`modules/auth/...` (`JwtTokenIssuer` com `issueForUser`/`issueForLogin`, `UserMicroservice` via
`RestClient`, `JwksController`). Contratos: `docs/api-contracts.md` §1–§5; claims e escopos em
`openspec/config.yaml`.

## Goals / Non-Goals

**Goals:** borda única e sem estado (exceto Redis); nenhum token do browser sai do gateway.

**Non-Goals:** Spring Cloud Gateway (sem dependência nova); circuit breaker; webhook de
transportadora; refresh token separado; cookie.

## Decisions

### D1. Duas cadeias de segurança
- `@Order(1)` `apiChain`: `securityMatcher("/api/v1/**", "/auth/**", "/public/**", "/.well-known/**")`,
  STATELESS, csrf off, CORS. `permitAll` para: JWKS, `POST /auth/service-token`,
  `/public/webhooks/**` e as rotas públicas de `docs/api-contracts.md` §2.4 **com o método**
  (GET products/**, categories/**, users/{id}, shipping/quote, payments/config). Resto
  `authenticated()`. `oauth2ResourceServer().jwt()` com um `JwtDecoder` construído da chave
  pública do próprio `RSAKey` e validadores: timestamp, issuer, `aud` contém `front`, e
  `jti` fora da denylist do Redis.
- `@Order(2)` `loginChain`: o `oauth2Login` atual, para `/oauth2/**` e `/login/**`.
Por quê: o login precisa de sessão HTTP durante o fluxo OAuth; a API não pode ter sessão.

### D2. Proxy próprio com RestClient
`modules/proxy`: `ProxyController` com `@RequestMapping("/api/v1/**")` (rotas `/api/v1/auth/**`
têm controller próprio, mais específico). `RouteTable` mapeia o primeiro segmento:
users→user, products|categories|reviews→inventory, cart|orders→order, payments→payment,
shipments|shipping→shipment; qualquer outro (inclusive `internal`) → 404 ProblemDetail.
Repassa método, caminho sem `/api/v1`, query string, corpo em bytes e os headers
`Content-Type, Accept, Idempotency-Key, If-None-Match, If-Match, X-Request-Id`; troca
`Authorization` pelo token interno. Devolve status e corpo intactos e os headers
`Content-Type, Location (reescrito com o prefixo /api/v1), ETag, Cache-Control, Retry-After,
Idempotency-Replayed`. Timeouts: connect 2 s, read 5 s (15 s para `payments`). Conexão recusada
→ 503; timeout → 504; ambos ProblemDetail.
Alternativa descartada: Spring Cloud Gateway — dependência grande e incerta no Boot 4.1.

### D3. Token interno por papel
`TokenIssuerPort` ganha `issueInternal(String sub, List<String> roles, Set<String> scopes)`
(TTL `app.jwt.service-ttl`, `aud=internal`, claims `scope`, `roles`, `reqId`) e
`issueForService(String clientId)` (`sub=svc:<id>`, scope `internal:hydrate`, TTL 5 min).
`ScopePolicy` resolve o conjunto de escopos do papel (tabela do config). Papel vem do
`ProfileCache`.

### D4. ProfileCache
Redis `auth:profile:{id}` (JSON `{id,name,email,photoUrl,roles}`, TTL 5 min). Miss → `user`
`GET /internal/users/{id}/profile` com token de serviço emitido localmente
(`issueForService("api-gateway")`). Redis fora → vai direto ao `user`.

### D5. Sessão
- Login: `AuthSuccessHandler` redireciona para `{front}/callback#token=…`; o token do browser
  ganha `roles` (da resposta do `user`) e `auth_time` (agora).
- `GET /api/v1/auth/session`: perfil do cache + `expiresAt` do token.
- `POST /api/v1/auth/refresh`: token válido → novo token com o mesmo `auth_time`; se
  `now - auth_time > 7 dias` → 401 `SESSION_EXPIRED`; `jti` antigo na denylist (TTL até o `exp`).
- `POST /api/v1/auth/logout`: `jti` na denylist; 204. Redis fora → 503 nas duas.

### D6. Token de serviço
`POST /auth/service-token` `{clientId, clientSecret}`: segredo conferido contra
`app.service-clients.<id>` com `MessageDigest.isEqual`; ok → `{accessToken, tokenType:"Bearer",
expiresIn:300}`; desconhecido ou errado → 401 (mesma resposta, para não enumerar clientes).

### D7. Webhook do Mercado Pago
`modules/webhook`: header `x-signature: ts=<ts>,v1=<hex>` e `x-request-id`; `data.id` da query
(`data.id`) ou do corpo. Manifest `id:<data.id>;request-id:<x-request-id>;ts:<ts>;`,
HMAC-SHA256 com `app.mercadopago.webhook-secret`, comparação em tempo constante. `ts` a mais de
5 min → 408. Válido → `POST {payment}/webhooks/mercadopago` com corpo
`{signatureVerified:true, receivedAt, payload:<corpo original>}` e token interno
`sub=svc:api-gateway`, escopos `webhooks:ingest internal:hydrate`. Upstream 2xx → 200; 4xx de
negócio (404/409/422) → 200 (não adianta o MP reenviar); 5xx/timeout → 500/503 (o MP reenvia).

### D8. Filtros transversais
`RequestIdFilter` (gera UUID se ausente, devolve no response). `RateLimitFilter` em `/api/v1/**`:
Redis `INCR rl:{ip}:{epochMinute}` com expire 60 s, limite 120/min por IP; estouro → 429 com
`Retry-After`; erro de Redis → deixa passar. CORS: origem `app.front-url`, headers expostos
`Location, ETag, X-Request-Id, Idempotency-Replayed`.

## Risks / Trade-offs

- [Escopo por papel é mais largo que escopo por rota] → os serviços continuam checando escopo
  por rota; decisão registrada no config.
- [Proxy próprio não faz streaming] → corpos são pequenos (JSON); aceitável.
- [Webhook com 4xx de negócio respondido 200] → evita retry inútil do MP; o evento fica no log.

## Decisoes de Implementacao

- **UUID em vez de ULID para X-Request-Id**: Java 25 oferece UUID nativamente. ULID exigiria
  dependência externa sem versão no Maven Central. UUID com 128 bits de aleatoriedade e amarra
  logs com segurança suficiente.

- **SimpleClientHttpRequestFactory para timeouts**: RestClient requer suporte a timeouts de
  conexão e leitura. SimpleClientHttpRequestFactory oferece setConnectTimeout()/setReadTimeout()
  diretamente. JdkClientHttpRequestFactory (Java 21+) nao está disponível no Spring Boot 4.1.

- **ProfileCache trata silenciosamente falhas de Redis**: Conforme o design D4 e kit de plataforma,
  falhas de cache nunca derubam rotas. ProfileCache loga WARN e segue. Se cache falha,
  UserMicroservice.findProfile() traz do banco.

- **SessionController retorna 401 com code="SESSION_EXPIRED" em refresh**: Em vez de lançar exceção,
  o endpoint responde 401 JSON com o código de erro estável para o front ramificar. Redis indisponível
  também retorna 503 JSON.

- **ProxyController não repassa Authorization header do browser**: Token do browser (aud=front) é
  validado apenas no gateway. ProxyController emite novo token (aud=internal) com escopos da rota.
  Segue design D2 e a trava de audiência (§1.2 de api-contracts.md).

- **MercadoPagoWebhookController responde 200 para 404/409/422 do payment**: Por design D7, erros
  de negócio (recurso não encontrado, conflito) não justificam retry. O Mercado Pago deixa de
  reenviar. Erros 5xx retornam para o MP reenviar.

### Correções da revisão

- O proxy recebia `@AuthenticationPrincipal Optional<Jwt>`, que o Spring nunca preenche: o
  parâmetro chegava `null`. Passou a ser `Jwt` anulável (null = anônimo).
- A query string não era repassada (`getRequestURI()` não a inclui). O destino agora é montado com
  `getQueryString()` e enviado como `URI` pronta, para não ser codificado duas vezes.
- `Location` absoluto vindo do serviço (`http://user:8081/...`) é reduzido a caminho + query sob
  `/api/v1`.
- `/api/v1/users/me/**` e `/api/v1/products/manage/**` exigem token antes das regras públicas, que
  casariam com eles. Usuário com token válido mas sem perfil recebe 401, não 400.
