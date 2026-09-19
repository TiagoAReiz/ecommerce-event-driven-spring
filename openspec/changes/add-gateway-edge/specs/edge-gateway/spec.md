# Spec Delta

## Purpose

O gateway SHALL funcionar como proxy único da API pública, roteando requisições pelos primeiros segmentos, trocando tokens do browser por tokens internos e gerenciando rate limits, identificadores únicos de requisição e isolamento de rotas internas.

## ADDED Requirements

### Requirement: Proxy por prefixo de rota
O gateway SHALL rotear toda requisição `/api/v1/**` pelo primeiro segmento após `/api/v1` para o serviço downstream correspondente: `users` → `user:8081`, `products|categories|reviews` → `inventory:8082`, `cart|orders` → `order:8083`, `payments` → `payment:8084`, `shipments|shipping` → `shipment:8085`. O caminho SHALL ser repassado sem o prefixo `/api/v1`. Qualquer prefixo desconhecido SHALL devolver `404 ProblemDetail`.

#### Scenario: Requisição válida roteia para o serviço correto
- **WHEN** uma requisição chega em `GET /api/v1/products?page=0`
- **THEN** o gateway roteia para `inventory:8082` com path `/products?page=0`

#### Scenario: Rota interna é recusada com 404
- **WHEN** uma requisição tenta acessar `GET /api/v1/internal/users/42`
- **THEN** o gateway responde `404 ProblemDetail` (não confirma existência de rota interna)

#### Scenario: Prefixo desconhecido retorna 404
- **WHEN** uma requisição chega em `GET /api/v1/unknown/resource`
- **THEN** o gateway responde `404 ProblemDetail`

### Requirement: Troca de token do browser por token interno
O gateway SHALL validar o token do usuário (com `aud=front`) em toda requisição autenticada, resolver os escopos correspondentes ao papel do usuário e emitir um token interno novo (com `aud=internal`) para cada chamada downstream. O token do usuário do browser MUST NEVER ser repassado ao serviço downstream.

#### Scenario: Token válido é trocado por token interno
- **WHEN** uma requisição autenticada chega com Bearer token válido (`aud=front`)
- **THEN** o gateway emite novo token com `aud=internal` e repassa

#### Scenario: Token expirado retorna 401
- **WHEN** uma requisição chega com Bearer token expirado
- **THEN** o gateway responde `401` (decidido no gateway, não chega ao serviço)

#### Scenario: Token com audiência errada é rejeitado
- **WHEN** uma requisição chega com token que tem `aud=internal` ou outro valor
- **THEN** o gateway responde `401`

#### Scenario: Token ausente em rota protegida retorna 401
- **WHEN** uma requisição protegida chega sem Authorization header
- **THEN** o gateway responde `401`

### Requirement: Rotas públicas atendidas com token de serviço
O gateway SHALL atender requisições públicas (`GET /api/v1/products`, `GET /api/v1/products/{id}`, `GET /api/v1/products/{id}/photos`, `GET /api/v1/products/{id}/reviews`, `GET /api/v1/products/{id}/availability`, `GET /api/v1/categories`, `GET /api/v1/categories/{idOrSlug}`, `GET /api/v1/users/{id}`, `GET /api/v1/shipping/quote`, `GET /api/v1/payments/config`) sem exigir token do usuário, emitindo internamente um token de serviço com escopos públicos (`catalog:read` e similares).

#### Scenario: GET público é atendido sem Bearer token
- **WHEN** uma requisição chega em `GET /api/v1/products` sem Authorization header
- **THEN** o gateway emite token de serviço internamente e repassa ao inventory

#### Scenario: Outros métodos em rota pública exigem autenticação
- **WHEN** uma requisição chega em `POST /api/v1/products` sem Bearer token
- **THEN** o gateway responde `401`

### Requirement: Rate limit por IP
O gateway SHALL implementar rate limit de 120 requisições por minuto por endereço IP em rotas `/api/v1/**`. Quando o limite é excedido, SHALL responder `429` com header `Retry-After`.

#### Scenario: Requisições dentro do limite passam
- **WHEN** um cliente faz até 120 requisições em um minuto
- **THEN** o gateway aceita todas

#### Scenario: Requisição acima do limite retorna 429
- **WHEN** um cliente excede 120 requisições em um minuto
- **THEN** o gateway responde `429` com `Retry-After`

#### Scenario: Queda do Redis não bloqueia rate limit
- **WHEN** Redis está indisponível e uma requisição chega
- **THEN** o gateway loga WARN e deixa passar (rate limit é otimização)

### Requirement: Geração e propagação de X-Request-Id
O gateway SHALL gerar um identificador único (ULID) em `X-Request-Id` se ausente na requisição recebida, e SHALL repassar este header ao serviço downstream e devolver na resposta. O mesmo `X-Request-Id` SHALL constar no claim `reqId` do token interno.

#### Scenario: Requisição sem X-Request-Id recebe um gerado
- **WHEN** uma requisição chega sem header X-Request-Id
- **THEN** o gateway gera um ULID e devolve em X-Request-Id na resposta

#### Scenario: Requisição com X-Request-Id existente é preservada
- **WHEN** uma requisição chega com X-Request-Id já definido
- **THEN** o gateway usa este mesmo header na resposta e no token interno

### Requirement: Headers repassados e transformados
O gateway SHALL repassar headers de requisição: `Content-Type`, `Accept`, `Idempotency-Key`, `If-None-Match`, `If-Match`, `X-Request-Id`. O header `Authorization` SHALL ser substituído pelo token interno. A resposta SHALL devolver headers: `Content-Type`, `Location` (com prefixo `/api/v1` adicionado), `ETag`, `Cache-Control`, `Retry-After`, `Idempotency-Replayed`.

#### Scenario: Headers corretos são repassados para o serviço
- **WHEN** uma requisição chega com `Content-Type: application/json`, `If-None-Match: abc123`
- **THEN** estes headers são repassados ao serviço downstream

#### Scenario: Authorization é substituído por token interno
- **WHEN** uma requisição autenticada chega com `Authorization: Bearer <token-front>`
- **THEN** o gateway substitui por `Authorization: Bearer <token-internal>` ao repassar

#### Scenario: Location na resposta é reescrito com prefixo /api/v1
- **WHEN** o serviço responde `201` com `Location: /products/123`
- **THEN** o gateway devolve `Location: /api/v1/products/123`

### Requirement: Tratamento de falhas e timeouts
O gateway SHALL respeitar timeouts de conexão (2 s) e leitura (5 s, 15 s para `payments`). Conexão recusada SHALL resultar em `503`. Timeout SHALL resultar em `504`. Ambas as respostas SHALL ser `ProblemDetail`.

#### Scenario: Serviço indisponível retorna 503
- **WHEN** o serviço downstream recusa a conexão
- **THEN** o gateway responde `503 Service Unavailable`

#### Scenario: Timeout de leitura retorna 504
- **WHEN** a requisição ao serviço excede o timeout configurado
- **THEN** o gateway responde `504 Gateway Timeout`

#### Scenario: Payment service recebe timeout maior
- **WHEN** uma requisição a `payment` é feita
- **THEN** o timeout de leitura SHALL ser 15 segundos
