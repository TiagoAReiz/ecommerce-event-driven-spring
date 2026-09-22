# Contratos de API — Loja Event-Driven

> Documento de arquitetura. Define **toda** a superfície HTTP do sistema: rotas, corpos de
> entrada e saída, parâmetros e o catálogo completo de códigos de resposta por rota.
>
> Status de cada rota:
> - `IMPLEMENTADO` — já existe no código
> - `PLANEJADO` — desenhado aqui, ainda não escrito
>
> Última revisão: 2026-09-18

---

## Sumário

1. [Topologia e regras de borda](#1-topologia-e-regras-de-borda)
2. [Modelo de autenticação e autorização](#2-modelo-de-autenticação-e-autorização)
3. [Convenções transversais](#3-convenções-transversais)
4. [Cache com Redis](#4-cache-com-redis)
5. [API Gateway `:8080`](#5-api-gateway-8080)
6. [Serviço `user` `:8081`](#6-serviço-user-8081)
7. [Serviço `inventory` `:8082`](#7-serviço-inventory-8082)
8. [Serviço `order` `:8083`](#8-serviço-order-8083)
9. [Serviço `payment` `:8084` — Mercado Pago](#9-serviço-payment-8084--mercado-pago)
10. [Serviço `shipment` `:8085`](#10-serviço-shipment-8085)
11. [Contrato de eventos Kafka](#11-contrato-de-eventos-kafka)
12. [Fluxo ponta a ponta do checkout](#12-fluxo-ponta-a-ponta-do-checkout)
13. [Decisões registradas e pendências](#13-decisões-registradas-e-pendências)

---

## 1. Topologia e regras de borda

```
                       ┌──────────────────────────────────────────┐
  browser ──HTTPS──►   │  api-gateway :8080   (ÚNICA porta        │
  Mercado Pago ────►   │                       publicada)         │
                       └───────────────┬──────────────────────────┘
                                       │ token aud=internal, TTL curto
        ┌──────────────┬───────────────┼───────────────┬──────────────┐
        ▼              ▼               ▼               ▼              ▼
    user :8081   inventory :8082   order :8083   payment :8084  shipment :8085
        │              │               │               │              │
        └──────────────┴──────┬────────┴───────────────┴──────────────┘
                              ▼
                  Kafka (broker:9092)  ·  Redis (redis:6379)
                  PostgreSQL — 1 banco por serviço, sem FK cruzada
```

### As quatro travas, em ordem de importância

| # | Trava | Onde é aplicada |
|---|---|---|
| 1 | **Rede** — só o gateway publica porta. Não existe rota da internet até os demais. | `docker-compose.yaml`: nenhum serviço além do gateway tem `ports:` |
| 2 | **Audiência** — o token do browser sai com `aud=front`; todo serviço interno exige `aud=internal`. O token do usuário, mesmo assinado pela mesma chave, não abre nada na rede interna. | `JwtTokenIssuer` + `spring.security.oauth2.resourceserver.jwt.audiences=internal` |
| 3 | **Escopo** — o token interno carrega só os escopos da rota chamada, nunca um escopo coringa. | `SecurityConfig` de cada serviço, com `hasAuthority("SCOPE_…")` |
| 4 | **Sem anônimo** — nenhum serviço interno tem rota `permitAll`. O catálogo, que é público para o usuário, é público **no gateway**; o gateway ainda assim assina um token de serviço para buscá-lo. | `anyRequest().authenticated()` em cada serviço |

> **Consequência prática:** o token que o usuário tem em mãos nunca é repassado adiante. O
> gateway o valida, extrai `sub`, e **emite um token novo** para cada chamada downstream. Não
> existe passagem de credencial do cliente para dentro da malha.

### Portas

| Serviço | Porta | Publicada no host | Banco |
|---|---|---|---|
| api-gateway | 8080 | **sim** | — |
| user | 8081 | não | `user_db` |
| inventory | 8082 | não | `inventory_db` |
| order | 8083 | não | `order_db` |
| payment | 8084 | não | `payment_db` |
| shipment | 8085 | não | `shipment_db` |

---

## 2. Modelo de autenticação e autorização

### 2.1 Os dois tokens

| | Token do usuário | Token interno |
|---|---|---|
| **Emissor** | gateway (RS256, chave privada montada em volume) | gateway |
| **`aud`** | `front` | `internal` |
| **`sub`** | id em `users.id` | id do usuário, ou `api-gateway` quando o gateway age sozinho |
| **TTL** | 1 h (`app.jwt.ttl`) | 1 min (`app.jwt.service-ttl`) |
| **Onde vive** | browser | apenas em trânsito, gateway → serviço |
| **Escopos** | nenhum (não autoriza nada internamente) | só os escopos da rota chamada |

**Claims do token interno:**

```json
{
  "iss": "http://localhost:8080",
  "aud": ["internal"],
  "sub": "42",
  "scope": "orders:write cart:read",
  "roles": ["customer"],
  "act": "api-gateway",
  "reqId": "01JB8X9K2M4N6P8Q0R2S4T6V8W",
  "iat": 1789564800,
  "exp": 1789564860,
  "jti": "b3f1a9c2-…"
}
```

- `sub` — **fonte única de verdade de quem age**. Nenhuma rota aceita `userId` no corpo ou no
  path para identificar o autor: um serviço que confie em `userId` vindo do corpo permite que a
  trava do gateway seja contornada no dia em que alguém expuser outra porta.
- `roles` — resolvido pelo gateway a partir do cache `auth:profile:{userId}`
  (§4). Evita um round-trip ao `user` por requisição.
- `act` — quando o gateway age por conta própria (login, webhook), `sub` é `api-gateway` e não
  há `roles`.
- `reqId` — mesmo valor do header `X-Request-Id`; amarra o log de ponta a ponta.

### 2.2 Catálogo de escopos

| Escopo | Concede | Papel mínimo |
|---|---|---|
| `users:read` | ler perfil próprio e perfis públicos | qualquer |
| `users:write` | criar/alterar o próprio usuário | qualquer |
| `addresses:read` / `addresses:write` | endereços do próprio usuário | `customer` |
| `catalog:read` | ler produtos, categorias, fotos | anônimo |
| `catalog:write` | criar/alterar produtos, fotos e estoque da loja | `owner` |
| `reviews:read` / `reviews:write` | ler / escrever avaliação | `customer` |
| `cart:read` / `cart:write` | carrinho próprio | `customer` |
| `orders:read` / `orders:write` | pedidos próprios | `customer` |
| `sales:read` | todos os pedidos e envios da loja | `owner` |
| `payments:read` / `payments:write` | pagamentos dos pedidos próprios | `customer` |
| `payments:refund` | estornar e reconciliar pagamentos | `owner` |
| `shipments:read` / `shipments:write` | envios — a loja despacha, o comprador confirma o recebimento | `customer` / `owner` |
| `webhooks:ingest` | gravar o resultado de um webhook | **só o gateway** |
| `internal:hydrate` | leituras servidor-a-servidor de snapshot | só serviços |

### 2.3 Papéis

| Papel | Como é atribuído |
|---|---|
| `customer` | todo usuário autenticado |
| `owner` | a conta da loja: **uma única** linha ativa em `owner`, criada automaticamente para o e-mail de `app.store.owner-email` (§6) |

> **Não há papel `admin`.** O que dependeria dele foi redesenhado: categorias nascem por
> migration (§7), estorno e reconciliação ficam com o `owner` (§9).

> **Loja única, não marketplace.** Existe um só vendedor, a própria loja. Ninguém vira
> vendedor pela API, e nenhum contrato deste documento recebe ou devolve `idOwner`.

### 2.4 Rotas públicas (sem token do usuário)

São públicas **apenas na borda**. O gateway emite um token de serviço com `catalog:read` e
chama o `inventory` normalmente — o serviço continua sem rota anônima.

```
GET  /api/v1/products
GET  /api/v1/products/{id}
GET  /api/v1/products/{id}/photos
GET  /api/v1/products/{id}/reviews
GET  /api/v1/products/{id}/availability
GET  /api/v1/categories
GET  /api/v1/categories/{idOrSlug}
GET  /api/v1/users/{id}          (perfil público reduzido)
GET  /api/v1/shipping/quote      (frete antes do login)
GET  /api/v1/payments/config     (public key do Mercado Pago)
```

---

## 3. Convenções transversais

### 3.1 Prefixo e roteamento

Tudo que o cliente consome vive sob **`/api/v1`**. O gateway remove o prefixo e roteia pelo
primeiro segmento — o caminho downstream é sempre o caminho público menos `/api/v1`.

| Prefixo público | Destino |
|---|---|
| `/api/v1/auth/**` | gateway (não sai) |
| `/api/v1/users/**` | `user:8081` |
| `/api/v1/products/**`, `/api/v1/categories/**`, `/api/v1/reviews/**` | `inventory:8082` |
| `/api/v1/cart/**`, `/api/v1/orders/**` | `order:8083` |
| `/api/v1/payments/**` | `payment:8084` |
| `/api/v1/shipments/**`, `/api/v1/shipping/**` | `shipment:8085` |

Rotas de gestão da loja ficam **dentro do namespace do próprio serviço**, com o sufixo
`/manage` (`/products/manage`, `/orders/manage`, `/shipments/manage`), justamente para que o
roteamento continue decidível pelo primeiro segmento. Um `/admin/**` transversal obrigaria o
gateway a olhar o segundo segmento para escolher o destino.

Rotas `/internal/**` **nunca** são roteadas pelo gateway. Existem só para tráfego
servidor-a-servidor e devem ser recusadas na borda com `404`, não `403` — não vale confirmar
que existem.

### 3.2 Envelope de erro — RFC 9457

`Content-Type: application/problem+json` em toda resposta ≥ 400.

```json
{
  "type": "https://api.loja.dev/errors/insufficient-stock",
  "title": "Estoque insuficiente",
  "status": 409,
  "detail": "Produto 118 tem 2 unidades disponíveis, foram pedidas 5.",
  "instance": "/api/v1/orders",
  "code": "INSUFFICIENT_STOCK",
  "requestId": "01JB8X9K2M4N6P8Q0R2S4T6V8W",
  "timestamp": "2026-09-17T14:03:11.482Z",
  "errors": [
    { "field": "items[0].quantity", "message": "máximo disponível: 2" }
  ]
}
```

- `code` é estável e feito para o front ramificar; `title`/`detail` são para humanos.
- `errors[]` só aparece em `400`/`422`.
- **Nunca** vaza stack trace, SQL, nome de tabela ou hostname interno.

### 3.3 Headers

**Requisição (cliente → gateway)**

| Header | Obrigatório | Nota |
|---|---|---|
| `Authorization: Bearer <token>` | nas rotas autenticadas | token com `aud=front` |
| `Idempotency-Key` | nas rotas marcadas | UUID v4 do cliente, reaproveitado no retry |
| `X-Request-Id` | não | ausente ⇒ o gateway gera um ULID |
| `If-None-Match` | não | leituras cacheáveis respondem `304` |

**Resposta**

| Header | Quando |
|---|---|
| `X-Request-Id` | sempre |
| `ETag` / `Cache-Control` | leituras cacheáveis |
| `Location` | todo `201` |
| `Retry-After` | `429` e `503` |
| `RateLimit-Limit` / `-Remaining` / `-Reset` | rotas com limite |

### 3.4 Paginação

Query: `page` (0-based, default `0`), `size` (default `20`, máx `100`), `sort`
(`campo,asc|desc`, repetível).

```json
{
  "content": [ { "…": "…" } ],
  "page": { "number": 0, "size": 20, "totalElements": 137, "totalPages": 7 },
  "sort": ["createdAt,desc"]
}
```

`page` além do último devolve `200` com `content: []` — não `404`.

### 3.5 Idempotência

Obrigatória em `POST /orders`, `POST /payments` e `POST /payments/{id}/refund`.

1. Cliente envia `Idempotency-Key: <uuid>`.
2. O serviço grava `idem:{rota}:{sub}:{key}` no Redis com a resposta completa (TTL 24 h).
3. Repetição com a **mesma** chave e o **mesmo** corpo → devolve a resposta gravada com
   `Idempotency-Replayed: true`. O status original é preservado (um `201` replayed continua
   `201`).
4. Mesma chave, corpo **diferente** → `422 IDEMPOTENCY_KEY_REUSED`.
5. Chave ainda em processamento → `409 IDEMPOTENCY_IN_FLIGHT` com `Retry-After: 1`.

No `payment` a chave também é persistida em `payment.idempotency_key` (`UNIQUE`, já existe na
migration) e repassada ao Mercado Pago no header `X-Idempotency-Key` — a garantia atravessa a
fronteira do provedor, que é onde ela realmente importa.

### 3.6 Códigos que valem em qualquer rota

Repetidos nas tabelas de cada rota por completude; a semântica é sempre esta:

| Código | Significado |
|---|---|
| `400` | JSON malformado, tipo errado, parâmetro inválido |
| `401` | token ausente, expirado, assinatura inválida ou `aud` errada |
| `403` | token válido, mas sem o escopo exigido |
| `404` | recurso inexistente **ou** existente e não pertencente a quem chama |
| `405` | método não suportado no path |
| `406` | `Accept` incompatível com `application/json` |
| `415` | `Content-Type` diferente de `application/json` num corpo obrigatório |
| `429` | limite de requisições estourado |
| `500` | falha não prevista |
| `502` | downstream (ou Mercado Pago) devolveu resposta inválida |
| `503` | downstream indisponível, circuit breaker aberto |
| `504` | timeout no downstream |

> **`403` vs `404` em recurso de terceiro:** a regra do projeto é `404`. Devolver `403` para o
> pedido de outro usuário confirma que aquele id existe, o que permite enumerar a base. `403`
> fica reservado para *escopo insuficiente sobre o próprio recurso*.

### 3.7 Soft delete

Tabelas com `deleted_at` respondem `204` no `DELETE` e passam a devolver `404` nas leituras.
`orders`, `order_item` e `payment` **não** têm exclusão: pedido e dinheiro são histórico, o
encerramento é por status.

### 3.8 Dinheiro e datas

- Valores monetários: **string decimal** `"129.90"`, nunca `float`. Moeda fixa `BRL`.
- Datas: ISO-8601 UTC com `Z` — `"2026-09-17T14:03:11.482Z"`.
- Ids: inteiros de 64 bits, serializados como **número** JSON.

---

## 4. Cache com Redis

Uma instância, bancos lógicos separados por serviço (`db 0` gateway … `db 5` shipment) para que
um `FLUSHDB` de emergência não derrube os outros.

### 4.1 Mapa de chaves

| Chave | Serviço | Conteúdo | TTL | Invalidação |
|---|---|---|---|---|
| `auth:profile:{userId}` | gateway | `roles`, `name`, `email` | 5 min | `PATCH /users/me` |
| `auth:denylist:{jti}` | gateway | `1` | até o `exp` do token | logout; renovação (o token antigo morre quando o novo nasce) |
| `auth:jwks` | demais | JWKS do gateway | 10 min | rotação de chave |
| `rl:{ipOuSub}:{bucket}` | gateway | contador | janela | — |
| `idem:{rota}:{sub}:{key}` | order, payment | resposta serializada | 24 h | — |
| `catalog:product:{id}` | inventory | detalhe do produto | 10 min | `PUT`/`PATCH`/`DELETE` do produto, `PATCH /stock` |
| `catalog:search:{sha1(query)}` | inventory | página de resultados | 60 s | só TTL — é busca, aceita defasagem curta |
| `catalog:categories` | inventory | lista completa | 1 h | TTL **e** invalidação ao criar, alterar ou remover produto: a lista carrega `productCount` e, sem `includeEmpty`, só mostra categoria que tem produto |
| `catalog:hydrate:{id}` | inventory | `{id, name, price, photoUrl, available, active}` | 60 s | mesma do produto |
| `geo:cep:{cep}` | shipment | `{lat, lon, city, state}` do CEP | 30 d | só TTL — CEP não muda de lugar |
| `payment:methods:mp` | payment | meios de pagamento do MP | 6 h | só TTL |
| `payment:webhook:{mpEventId}` | payment | `1` (dedupe) | 24 h | — |

### 4.2 O que **não** entra em cache

| Item | Por quê |
|---|---|
| `GET /products/{id}/availability` | disponível = `stock − Σ reservas held não vencidas`. Muda a cada pedido criado; cachear aqui é vender o que não existe. |
| `GET /cart` | o carrinho é do usuário e muda a cada clique. O que se cacheia é o **snapshot do produto** usado para hidratá-lo (`catalog:hydrate:*`), não o carrinho. |
| `GET /orders`, `GET /payments/*` | dado transacional do próprio usuário; o ganho não paga o risco de servir estado velho de pedido ou de pagamento. |

### 4.3 Regras

- **Cache-aside**, nunca write-through: o serviço lê do Redis, e no miss lê do Postgres e grava.
- Queda do Redis **não** derruba rota nenhuma: falha de leitura/escrita no cache vira log
  `WARN` e segue para o banco. O Redis é otimização, não dependência de disponibilidade.
- Toda chave tem TTL. Chave sem TTL vira lixo permanente no primeiro bug de invalidação.
- `catalog:search:*` usa TTL curto em vez de invalidação: invalidar toda busca que *poderia*
  conter o produto alterado exigiria um índice reverso que custa mais que o próprio cache.

---

## 5. API Gateway `:8080`

Único serviço exposto. Responsabilidades: login Google, emissão de token, troca do token do
usuário pelo token interno, roteamento, rate limit e recepção de webhooks externos.

### 5.1 GET

#### `GET /oauth2/authorization/google` — `IMPLEMENTADO`

Início do login. Rota gerada pelo Spring Security, não há controller.

| | |
|---|---|
| **Auth** | nenhuma |
| **Parâmetros** | — |
| **Corpo** | — |

| Código | Quando |
|---|---|
| `302` | redireciona para o *consent screen* do Google (`Location: https://accounts.google.com/o/oauth2/v2/auth?…`) |
| `500` | `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` ausentes |
| `503` | gateway em shutdown |

---

#### `GET /login/oauth2/code/google` — `IMPLEMENTADO`

Callback do Google. Executa `AuthSuccessHandler`: busca o usuário por e-mail no `user`, cria no
primeiro acesso, assina o token e devolve o browser ao front.

| | |
|---|---|
| **Auth** | nenhuma (a prova é o `code` do Google) |
| **Query** | `code` (obrigatório), `state` (obrigatório), `error`, `error_description` |

| Código | Quando |
|---|---|
| `302` | sucesso → `Location: {app.front-url}/callback?token=…` |
| `302` | usuário negou o consentimento → `Location: {front}/login?error=access_denied` |
| `400` | `code` ou `state` ausente; `state` não confere (proteção CSRF do OAuth) |
| `401` | o Google recusou a troca do `code` por token |
| `409` | e-mail já pertence a outra conta Google (`googleSub` divergente) — ver `AuthSuccessHandler.getOrCreate` |
| `500` | falha ao assinar o JWT (chave ilegível), falha de I/O no redirect |
| `502` | `user` devolveu resposta não-2xx fora de `404` |
| `503` | `user` fora do ar |
| `504` | timeout na chamada ao `user` |

> **Sessão por Bearer, sem cookie.** O front guarda o token e o envia em
> `Authorization: Bearer …`. Ele chega ao front neste redirect. Mitigação barata e
> compatível com essa decisão: usar fragmento (`/callback#token=…`) em vez de query string —
> fragmento não é enviado a servidor nenhum nem entra no header `Referer`. É trocar `?` por `#`
> no `AuthSuccessHandler` e ler `location.hash` no front (§13.2).

---

#### `GET /.well-known/jwks.json` — `IMPLEMENTADO`

Chave **pública** para os demais serviços validarem os tokens. É `permitAll` de propósito.

**Resposta `200`**

```json
{
  "keys": [
    {
      "kty": "RSA",
      "e": "AQAB",
      "use": "sig",
      "kid": "sYqQ8_9oMkS1pQ…",
      "alg": "RS256",
      "n": "xGOr-H7A-PWG…"
    }
  ]
}
```

| Código | Quando |
|---|---|
| `200` | sempre que o par RSA carregou no boot (`Cache-Control: public, max-age=600`) |
| `500` | par RSA não pôde ser lido do volume |
| `503` | gateway em shutdown |

---

#### `GET /api/v1/auth/session` — `PLANEJADO`

Quem sou eu. Devolve o usuário do token sem obrigar o front a decodificar JWT.

| | |
|---|---|
| **Auth** | Bearer do usuário (`aud=front`) |
| **Cache** | `auth:profile:{sub}`, 5 min |

**Resposta `200`**

```json
{
  "id": 42,
  "name": "Tiago Reis",
  "email": "tiago@exemplo.com",
  "photoUrl": "https://lh3.googleusercontent.com/a/…",
  "roles": ["customer"],
  "expiresAt": "2026-09-17T15:03:11Z"
}
```

| Código | Quando |
|---|---|
| `200` | token válido |
| `401` | token ausente, expirado, assinatura inválida, `aud` ≠ `front`, ou `jti` na denylist |
| `404` | `sub` do token não existe mais em `users` (conta removida com token ainda vivo) |
| `429` | rate limit |
| `500` | falha não prevista |
| `503` | `user` indisponível e cache frio |

---

#### `GET /actuator/health` — `PLANEJADO`

| Código | Quando |
|---|---|
| `200` | `{"status":"UP"}` |
| `503` | `{"status":"DOWN"}` — chave RSA ilegível ou Redis exigido e fora |

---

#### `GET /api/v1/**` (proxy) — `PLANEJADO`

Toda leitura dos demais serviços. O gateway valida o token do usuário, resolve papéis, emite
token interno com os escopos daquela rota e repassa.

| Código | Quando |
|---|---|
| `2xx`/`4xx` | repassado do downstream, com o corpo original |
| `401` | token do usuário inválido — **decidido no gateway**, não chega a sair |
| `403` | rota exige papel que o usuário não tem (ex.: `/products/manage` sem `owner`) |
| `404` | prefixo não corresponde a nenhuma rota, ou tentativa de acessar `/internal/**` |
| `429` | rate limit do gateway |
| `502` | downstream devolveu corpo que não é JSON válido |
| `503` | circuit breaker aberto para aquele serviço (`Retry-After`) |
| `504` | timeout (padrão 5 s de leitura) |

### 5.2 POST

#### `POST /api/v1/auth/refresh` — `PLANEJADO`

Renovação **deslizante**: troca um token ainda válido por um novo, sem voltar ao Google. Não
existe refresh token separado nem cookie — o próprio Bearer é a credencial de renovação.

| | |
|---|---|
| **Auth** | Bearer do usuário, **ainda dentro do `exp`** |
| **Corpo** | vazio |

**Regras**

- O token novo herda a claim `auth_time` (instante do login no Google). Se
  `now − auth_time > 7 dias`, a renovação é recusada: é o teto da sessão, depois disso é login
  de novo. Sem esse teto, um token vazado se renovaria para sempre.
- O `jti` do token antigo vai para `auth:denylist:{jti}` até o `exp` dele: **cada token renova
  uma única vez**. Um segundo refresh com o mesmo token indica cópia.
- Token expirado não renova. A janela é o próprio TTL de 1 h; o front renova quando faltar
  ~5 min.

**Resposta `200`**

```json
{
  "accessToken": "eyJraWQiOi…",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-09-17T15:03:11Z",
  "sessionExpiresAt": "2026-09-24T14:00:00Z"
}
```

| Código | Quando |
|---|---|
| `200` | novo token emitido |
| `401` | token ausente, expirado, assinatura inválida, `aud` ≠ `front`, ou já renovado (`jti` na denylist) |
| `401` | `SESSION_EXPIRED` — passou do teto de 7 dias desde o login |
| `429` | mais de 10 renovações/min pelo mesmo usuário |
| `500` | falha ao assinar |
| `503` | Redis fora — sem denylist não há como garantir o uso único, então falha explicitamente |

---

#### `POST /api/v1/auth/logout` — `PLANEJADO`

| | |
|---|---|
| **Auth** | Bearer do usuário |
| **Corpo** | vazio |

Grava o `jti` do token em `auth:denylist:{jti}` até o `exp`. A partir daí o gateway recusa esse
token mesmo com a assinatura válida. O front descarta o token da memória.

| Código | Quando |
|---|---|
| `204` | sessão encerrada |
| `401` | token ausente ou inválido |
| `500` | falha não prevista |
| `503` | Redis fora — logout sem denylist é mentira, então falha explicitamente |

---

#### `POST /public/webhooks/mercadopago` — `PLANEJADO`

Endpoint que o Mercado Pago chama. É `permitAll` na borda (o MP não tem token nosso); a
autenticidade vem da assinatura HMAC.

| | |
|---|---|
| **Auth** | assinatura `x-signature` + `x-request-id` |
| **Headers** | `x-signature: ts=1789564800,v1=abc123…`, `x-request-id`, `Content-Type: application/json` |
| **Query** | `data.id`, `type` (o MP envia nos dois lugares) |

**Corpo recebido do MP**

```json
{
  "id": 12345678901,
  "live_mode": true,
  "type": "payment",
  "date_created": "2026-09-17T14:03:00.000-04:00",
  "user_id": "1234567890",
  "api_version": "v1",
  "action": "payment.updated",
  "data": { "id": "119384756201" }
}
```

**Validação do manifest** (ordem exata, minúsculo, com `;` final):

```
id:<data.id>;request-id:<x-request-id>;ts:<ts>;
HMAC-SHA256(manifest, MP_WEBHOOK_SECRET) == v1
```

O gateway rejeita `ts` com mais de 5 min de diferença (replay), depois repassa para
`payment:8084 POST /webhooks/mercadopago` com token interno de escopo `webhooks:ingest`.

**Resposta `200`** — corpo vazio. O MP só quer `2xx`; qualquer outra coisa entra na fila de
retry dele (até ~8 tentativas com backoff).

| Código | Quando |
|---|---|
| `200` | aceito (inclusive evento duplicado — dedupe é silencioso) |
| `400` | corpo sem `data.id` ou sem `type` |
| `401` | `x-signature` ausente, malformada ou HMAC divergente |
| `408` | `ts` fora da janela de 5 min (replay) |
| `422` | `type` não tratado (`plan`, `subscription`, `invoice`) — não reprocessar |
| `500` | falha ao repassar; **o MP vai reenviar**, e é isso que se quer |
| `503` | `payment` indisponível — idem, provoca retry do MP |

> Responder `200` para evento que não conseguimos processar é o pior resultado possível: perde
> a notificação de pagamento em silêncio. Erro interno **deve** devolver `5xx`.

---

#### `POST /api/v1/**` (proxy) — `PLANEJADO`

Mesma tabela do proxy `GET` (§5.1), acrescida de:

| Código | Quando |
|---|---|
| `413` | corpo acima de 1 MB (10 MB em upload de foto) |
| `415` | `Content-Type` não é `application/json` (ou `multipart/form-data` no upload) |

---

## 6. Serviço `user` `:8081`

Dono de `users`, `owner` e `address`. **Não guarda senha** — a identidade é o `google_sub`.

**A conta da loja.** Existe um único `owner`, e ele não nasce pela API. O `user` lê
`app.store.owner-email` da configuração e:

- ao criar o usuário com esse e-mail (`POST /users`, primeiro login), cria também a linha de
  `owner` na mesma transação;
- no boot, se esse usuário já existe e a linha de `owner` não, cria.

Qualquer outro usuário é só `customer`. O papel volta no campo `roles` da resposta de
`GET /users?email=`, que o gateway lê no login e guarda em `auth:profile:{userId}`.

Não é seed por migration porque `owner.id_user` tem FK para `users`, e o usuário só existe
depois do primeiro login com o Google.

### 6.1 GET

#### `GET /users?email={email}` — `IMPLEMENTADO`

Usado **só pelo gateway durante o login**. Não é exposto em `/api/v1`.

| | |
|---|---|
| **Auth** | token interno, `users:read` |
| **Query** | `email` (obrigatório) |

**Resposta `200`**

```json
{
  "id": 42,
  "name": "Tiago Reis",
  "email": "tiago@exemplo.com",
  "googleSub": "117482910384756201928",
  "photoUrl": "https://lh3.googleusercontent.com/a/…",
  "roles": ["customer"]
}
```

`roles` é por onde o gateway sabe, no login, se a conta é a da loja.

| Código | Quando |
|---|---|
| `200` | encontrado |
| `400` | `email` ausente |
| `401` | token inválido ou `aud` ≠ `internal` |
| `403` | token sem `SCOPE_users:read` |
| `404` | não existe (ou `deleted_at` preenchido) — **esperado no primeiro acesso**, o gateway trata como `Optional.empty()` |
| `500` | falha de banco |
| `503` | banco indisponível |

---

#### `GET /users/me` — `PLANEJADO`

Perfil completo do dono do token. O id vem de `sub`, nunca do path.

| | |
|---|---|
| **Auth** | `users:read` |
| **Público em** | `GET /api/v1/users/me` |

**Resposta `200`**

```json
{
  "id": 42,
  "name": "Tiago Reis",
  "email": "tiago@exemplo.com",
  "cpf": "12345678901",
  "phone": "+5511999998888",
  "photoUrl": "https://lh3.googleusercontent.com/a/…",
  "roles": ["customer"],
  "addressCount": 2,
  "createdAt": "2026-01-15T09:00:00Z",
  "updatedAt": "2026-09-10T18:42:11Z"
}
```

`roles` só inclui `owner` para a conta da loja.

| Código | Quando |
|---|---|
| `200` | ok |
| `401` | token inválido |
| `403` | sem `users:read` |
| `404` | `sub` não existe mais |
| `500` / `503` | falha ou indisponibilidade de banco |

---

#### `GET /users/{id}` — `PLANEJADO`

Perfil **público reduzido** (autor de avaliação). Nunca devolve `email`,
`cpf`, `phone` nem `googleSub`.

| | |
|---|---|
| **Auth** | `users:read` (o gateway emite mesmo para anônimo) |
| **Path** | `id` |
| **Cache** | `Cache-Control: public, max-age=300` + `ETag` |

**Resposta `200`**

```json
{ "id": 42, "name": "Tiago Reis", "photoUrl": "https://…", "memberSince": "2026-01-15" }
```

| Código | Quando |
|---|---|
| `200` | ok |
| `304` | `If-None-Match` bate |
| `400` | `id` não numérico |
| `401` / `403` | token interno inválido / sem escopo |
| `404` | inexistente ou removido |
| `500` / `503` | banco |

---

#### `GET /users/me/addresses` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `addresses:read` |
| **Query** | `page`, `size`, `sort` (default `createdAt,desc`) |

**Resposta `200`**

```json
{
  "content": [
    {
      "id": 15,
      "name": "casa",
      "zipcode": "01310100",
      "country": "BR",
      "state": "SP",
      "city": "São Paulo",
      "street": "Avenida Paulista",
      "number": "1578",
      "createdAt": "2026-02-01T10:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 2, "totalPages": 1 }
}
```

| Código | Quando |
|---|---|
| `200` | ok, inclusive lista vazia |
| `400` | `size` > 100 ou `sort` em campo inexistente |
| `401` / `403` | token / escopo |
| `500` / `503` | banco |

---

#### `GET /users/me/addresses/{id}` — `PLANEJADO`

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `id` não numérico |
| `401` / `403` | token / escopo |
| `404` | inexistente, removido, **ou de outro usuário** |
| `500` / `503` | banco |

---

#### `GET /internal/users?ids=1,2,3` — `PLANEJADO`

Hidratação em lote de snapshot (`review.user_name`, `review.user_photo_url`).

| | |
|---|---|
| **Auth** | `internal:hydrate` |
| **Query** | `ids` — CSV, máx. **100** |

**Resposta `200`**

```json
{ "users": [ { "id": 42, "name": "Tiago Reis", "photoUrl": "https://…" } ], "missing": [ 99 ] }
```

Id inexistente entra em `missing`, não estoura. Quem hidrata snapshot precisa saber o que
faltou sem perder o resto do lote.

| Código | Quando |
|---|---|
| `200` | ok (mesmo com tudo em `missing`) |
| `400` | `ids` ausente, vazio, não numérico ou acima de 100 |
| `401` / `403` | token / escopo |
| `500` / `503` | banco |

---

#### `GET /internal/addresses/{id}?userId={userId}` — `PLANEJADO`

Endereço completo, para o `order` validar o destino no checkout e para o `shipment` montar o
snapshot `to_*`.

| | |
|---|---|
| **Auth** | `internal:hydrate` |
| **Path** | `id` |
| **Query** | `userId` (obrigatório) — o endereço só é devolvido se pertencer a esse usuário |

`userId` obrigatório é o que permite ao `order` conferir que o `addressId` do checkout é do
cliente usando o `sub` do token, sem confiar em nada do corpo.

**Resposta `200`**

```json
{
  "id": 15,
  "idUser": 42,
  "name": "casa",
  "zipcode": "01310100",
  "country": "BR",
  "state": "SP",
  "city": "São Paulo",
  "street": "Avenida Paulista",
  "number": "1578"
}
```

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `userId` ausente; `id` não numérico |
| `401` / `403` | token / escopo |
| `404` | inexistente, removido ou de outro usuário |
| `500` / `503` | banco |

### 6.2 POST

#### `POST /users` — `IMPLEMENTADO`

Chamado **só pelo gateway**, no primeiro login. Não existe cadastro manual.

| | |
|---|---|
| **Auth** | `users:write` |

**Request**

```json
{
  "name": "Tiago Reis",
  "email": "tiago@exemplo.com",
  "googleSub": "117482910384756201928",
  "photoUrl": "https://lh3.googleusercontent.com/a/…"
}
```

`name`, `email` e `googleSub` são obrigatórios (`NOT NULL` no banco; o controller valida antes
para que a falha seja `400` e não `500`).

**Resposta `201`** — mesmo corpo de `GET /users?email=`, com `Location: /users/42`.

| Código | Quando |
|---|---|
| `201` | criado |
| `400` | `name`, `email` ou `googleSub` em branco |
| `401` / `403` | token / escopo `users:write` |
| `409` | `email` ou `googleSub` já existe (`DataIntegrityViolationException` → índice único parcial) |
| `415` | `Content-Type` ≠ `application/json` |
| `422` | `email` sintaticamente inválido |
| `500` / `503` | banco |

---

#### `POST /users/me/addresses` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `addresses:write` |

**Request**

```json
{
  "name": "casa",
  "zipcode": "01310100",
  "country": "BR",
  "state": "SP",
  "city": "São Paulo",
  "street": "Avenida Paulista",
  "number": "1578"
}
```

| Campo | Regra |
|---|---|
| `name` | opcional, ≤ 60 |
| `zipcode` | obrigatório, 8 dígitos (sem máscara) |
| `country` | opcional, default `BR`, ISO-3166 alpha-2 |
| `state` | obrigatório, UF de 2 letras quando `country=BR` |
| `city`, `street` | obrigatórios |
| `number` | opcional (`"s/n"` é aceito) |

| Código | Quando |
|---|---|
| `201` | criado, `Location: /users/me/addresses/15` |
| `400` | campo obrigatório ausente ou acima do tamanho |
| `401` / `403` | token / escopo |
| `415` | `Content-Type` |
| `422` | CEP inexistente na base de validação; UF incompatível com a cidade |
| `429` | mais de 20 endereços criados em 1 h |
| `500` / `503` | banco |

### 6.3 PUT

#### `PUT /users/me/addresses/{id}` — `PLANEJADO`

Substituição completa. Campo omitido vira `null` — para alteração parcial use `PATCH`.

Mesmo corpo do `POST`.

| Código | Quando |
|---|---|
| `200` | substituído, devolve o recurso |
| `400` | campo obrigatório ausente |
| `401` / `403` | token / escopo |
| `404` | inexistente, removido ou de outro usuário |
| `415` | `Content-Type` |
| `422` | CEP/UF inválidos |
| `500` / `503` | banco |

### 6.4 PATCH

#### `PATCH /users/me` — `PLANEJADO`

Campos alteráveis: `name`, `cpf`, `phone`, `photoUrl`. `email` e `googleSub` **não** são
editáveis — quem manda neles é o Google.

**Request** (todos opcionais, ao menos um)

```json
{ "name": "Tiago A. Reis", "cpf": "12345678901", "phone": "+5511999998888" }
```

| Código | Quando |
|---|---|
| `200` | atualizado, devolve o perfil completo |
| `400` | corpo vazio; tentativa de alterar `email`/`googleSub`/`id` |
| `401` / `403` | token / escopo `users:write` |
| `404` | `sub` não existe mais |
| `409` | CPF já pertence a outro usuário |
| `415` | `Content-Type` |
| `422` | CPF com dígito inválido, telefone fora de E.164 |
| `500` / `503` | banco |

---

#### `PATCH /users/me/addresses/{id}` — `PLANEJADO`

Mesma tabela do `PUT`, menos o `400` de campo obrigatório ausente (aqui tudo é opcional) e
mais `400` para corpo vazio.

### 6.5 DELETE

#### `DELETE /users/me` — `PLANEJADO`

Soft delete (`deleted_at = now()`), revoga a sessão e emite `ecommerce.user.deleted.v1` para os
demais serviços anonimizarem snapshots.

| Código | Quando |
|---|---|
| `204` | removido |
| `401` / `403` | token / escopo `users:write` |
| `404` | já removido |
| `409` | há pedido em aberto (`pending`, `paid`, `processing`, `shipped`) — a conta não sai com obrigação pendente; ou é a conta da loja, que não se apaga pela API |
| `500` / `503` | banco |

---

#### `DELETE /users/me/addresses/{id}` — `PLANEJADO`

| Código | Quando |
|---|---|
| `204` | removido (soft) |
| `401` / `403` | token / escopo |
| `404` | inexistente ou de outro usuário |
| `409` | é destino de envio não finalizado |
| `500` / `503` | banco |

> Endereço já usado em envio concluído **pode** ser removido: o `shipment` guarda snapshot
> achatado em `to_*`/`from_*`, então o histórico não se perde.

---

## 7. Serviço `inventory` `:8082`

Dono de `category`, `product`, `product_photos`, `stock_reservation`, `review` e
`review_eligibility`. É o serviço com maior volume de leitura e o principal beneficiário do
Redis.

**Regra de disponibilidade** (`V2__stock_reservation.sql`):

```
disponível = product.stock − Σ quantity das reservas 'held' com expires_at > now()
```

Reserva vencida não é apagada nem atualizada: ela simplesmente para de entrar na soma.

**Categorias são dado de referência.** Nascem por migration Flyway
(`V3__seed_categories.sql`), não por API — sem papel `admin`, não há quem as administre por
HTTP. Por isso a API de categorias é só leitura.

### 7.1 GET

#### `GET /categories` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `catalog:read` (público na borda) |
| **Query** | `includeEmpty` (bool, default `false`) |
| **Cache** | `catalog:categories`, 1 h · `Cache-Control: public, max-age=3600` |

**Resposta `200`**

```json
{
  "content": [
    { "id": 3, "name": "Eletrônicos", "slug": "eletronicos", "productCount": 412 }
  ]
}
```

| Código | Quando |
|---|---|
| `200` | ok |
| `304` | `ETag` |
| `400` | `includeEmpty` não booleano |
| `401` / `403` | token interno |
| `500` / `503` | banco |

---

#### `GET /categories/{idOrSlug}` — `PLANEJADO`

Aceita `3` ou `eletronicos`. O slug tem índice único parcial (`category_slug_uk`).

| Código | Quando |
|---|---|
| `200` | ok |
| `304` | `ETag` |
| `401` / `403` | token interno |
| `404` | inexistente ou removida |
| `500` / `503` | banco |

---

#### `GET /products` — `PLANEJADO`

Vitrine e busca. Rota mais chamada do sistema.

| | |
|---|---|
| **Auth** | `catalog:read` |
| **Cache** | `catalog:search:{sha1(query normalizada)}`, 60 s |

**Query**

| Parâmetro | Tipo | Default | Nota |
|---|---|---|---|
| `q` | string | — | busca em `name` (índice `product_name_idx` em `lower(name)`) e `description` |
| `categoryId` | long | — | repetível (OR) |
| `categorySlug` | string | — | alternativa a `categoryId` |
| `minPrice` / `maxPrice` | decimal | — | `product_price_idx` |
| `minRating` | decimal 0–5 | — | |
| `inStock` | bool | `false` | `true` ⇒ só disponível > 0 |
| `page` / `size` / `sort` | | `0` / `20` / `createdAt,desc` | `sort` aceita `price`, `rating`, `createdAt`, `name` |

**Resposta `200`**

```json
{
  "content": [
    {
      "id": 118,
      "name": "Teclado mecânico ABNT2",
      "price": "349.90",
      "rating": "4.60",
      "ratingCount": 52,
      "available": 12,
      "photoUrl": "https://cdn.loja.dev/p/118/0.webp",
      "category": { "id": 3, "name": "Eletrônicos", "slug": "eletronicos" }
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 412, "totalPages": 21 },
  "facets": {
    "categories": [ { "id": 3, "name": "Eletrônicos", "count": 412 } ],
    "priceRange": { "min": "19.90", "max": "8999.00" }
  }
}
```

| Código | Quando |
|---|---|
| `200` | ok, inclusive vazio |
| `304` | `ETag` |
| `400` | `minPrice` > `maxPrice`; `size` > 100; `sort` em campo não permitido; tipo errado |
| `401` / `403` | token interno |
| `422` | `q` com menos de 2 caracteres |
| `429` | rate limit de busca (60/min por IP anônimo) |
| `500` / `503` | banco |
| `504` | timeout da consulta (limite de 3 s) |

---

#### `GET /products/{id}` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `catalog:read` |
| **Cache** | `catalog:product:{id}`, 10 min |

**Resposta `200`**

```json
{
  "id": 118,
  "name": "Teclado mecânico ABNT2",
  "description": "Switch marrom, ABNT2, USB-C destacável.",
  "price": "349.90",
  "stock": 15,
  "available": 12,
  "rating": "4.60",
  "ratingCount": 52,
  "category": { "id": 3, "name": "Eletrônicos", "slug": "eletronicos" },
  "photos": [
    { "id": 901, "photoUrl": "https://cdn.loja.dev/p/118/0.webp", "position": 0 }
  ],
  "createdAt": "2026-04-10T12:00:00Z",
  "updatedAt": "2026-09-15T08:30:00Z"
}
```

`stock` (bruto) só aparece para o `owner`; para o público sai apenas `available`.

| Código | Quando |
|---|---|
| `200` | ok |
| `304` | `ETag` |
| `400` | `id` não numérico |
| `401` / `403` | token interno |
| `404` | inexistente ou `deleted_at` preenchido |
| `500` / `503` | banco |

---

#### `GET /products/{id}/availability` — `PLANEJADO`

Disponibilidade calculada na hora. **Nunca cacheada** (§4.2).

**Resposta `200`**

```json
{ "idProduct": 118, "stock": 15, "held": 3, "available": 12, "asOf": "2026-09-17T14:03:11Z" }
```

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `id` não numérico |
| `401` / `403` | token interno |
| `404` | produto inexistente |
| `500` / `503` | banco |

---

#### `GET /products/{id}/photos` — `PLANEJADO`

| Código | Quando |
|---|---|
| `200` | ok, ordenado por `position` |
| `304` | `ETag` |
| `401` / `403` | token interno |
| `404` | produto inexistente |
| `500` / `503` | banco |

---

#### `GET /products/{id}/reviews` — `PLANEJADO`

| | |
|---|---|
| **Query** | `rate` (1–5, filtra), `page`, `size`, `sort` (`createdAt,desc` \| `rate,desc`) |

**Resposta `200`**

```json
{
  "content": [
    {
      "id": 501,
      "rate": 5,
      "title": "Excelente",
      "description": "Digitação ótima, chegou em 3 dias.",
      "author": { "id": 42, "name": "Tiago Reis", "photoUrl": "https://…" },
      "createdAt": "2026-08-02T19:11:00Z",
      "editedAt": null
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 52, "totalPages": 3 },
  "summary": {
    "rating": "4.60",
    "ratingCount": 52,
    "distribution": { "5": 38, "4": 9, "3": 3, "2": 1, "1": 1 }
  }
}
```

`author` vem do snapshot `review.user_name` / `review.user_photo_url`, não de chamada ao `user`.

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `rate` fora de 1–5; paginação inválida |
| `401` / `403` | token interno |
| `404` | produto inexistente |
| `500` / `503` | banco |

---

#### `GET /products/manage` — `PLANEJADO`

Catálogo completo, para gestão da loja. Mostra `stock` bruto, itens com `deleted_at` (se pedido) e os
que estão zerados.

| | |
|---|---|
| **Auth** | `catalog:write` + papel `owner` |
| **Query** | `status` (`active` \| `out_of_stock` \| `deleted` \| `all`, default `active`), `q`, `page`, `size`, `sort` |

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `status` fora do enum |
| `401` | token inválido |
| `403` | sem `catalog:write` **ou** usuário não é `owner` |
| `500` / `503` | banco |

---

#### `GET /reviews/{id}` — `PLANEJADO`

| Código | Quando |
|---|---|
| `200` | ok |
| `401` / `403` | token interno |
| `404` | inexistente ou removida |
| `500` / `503` | banco |

---

#### `GET /reviews/mine` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `reviews:read` |
| **Query** | `page`, `size`, `sort` |

| Código | Quando |
|---|---|
| `200` | ok, inclusive vazio |
| `401` / `403` | token / escopo |
| `500` / `503` | banco |

---

#### `GET /reviews/pending` — `PLANEJADO`

Produtos que o usuário **pode** avaliar: linhas de `review_eligibility` sem `review`
correspondente. É o que alimenta o "avalie sua compra".

**Resposta `200`**

```json
{
  "content": [
    {
      "idProduct": 118,
      "productName": "Teclado mecânico ABNT2",
      "productPhotoUrl": "https://cdn.loja.dev/p/118/0.webp",
      "idOrder": 3301,
      "grantedAt": "2026-08-01T10:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

| Código | Quando |
|---|---|
| `200` | ok |
| `401` / `403` | token / escopo `reviews:read` |
| `500` / `503` | banco |

---

#### `GET /internal/products?ids=1,2,3` — `PLANEJADO`

Hidratação de carrinho e revalidação de checkout.

| | |
|---|---|
| **Auth** | `internal:hydrate` |
| **Query** | `ids` — CSV, máx. 100 |
| **Cache** | `catalog:hydrate:{id}`, 60 s |

**Resposta `200`**

```json
{
  "products": [
    {
      "id": 118,
      "name": "Teclado mecânico ABNT2",
      "price": "349.90",
      "photoUrl": "https://cdn.loja.dev/p/118/0.webp",
      "available": 12,
      "active": true
    }
  ],
  "missing": [ 999 ]
}
```

`active: false` marca produto com `deleted_at` — o carrinho precisa exibi-lo como indisponível
em vez de sumir com o item sem explicação.

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `ids` ausente, vazio ou acima de 100 |
| `401` / `403` | token / escopo |
| `500` / `503` | banco |

---

#### `GET /internal/reservations?orderId={id}` — `PLANEJADO`

Diagnóstico da saga: em que estado está a reserva de um pedido.

**Resposta `200`**

```json
{
  "idOrder": 3301,
  "items": [
    { "idProduct": 118, "quantity": 1, "status": "held", "expiresAt": "2026-09-17T14:33:11Z" }
  ]
}
```

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `orderId` ausente |
| `401` / `403` | token / escopo `internal:hydrate` |
| `404` | não há reserva para esse pedido |
| `500` / `503` | banco |

### 7.2 POST

#### `POST /products` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `catalog:write` + papel `owner` |

**Request**

```json
{
  "name": "Teclado mecânico ABNT2",
  "description": "Switch marrom, ABNT2, USB-C destacável.",
  "idCategory": 3,
  "price": "349.90",
  "stock": 15,
  "photos": [
    { "photoUrl": "https://cdn.loja.dev/p/tmp/abc.webp", "position": 0 }
  ]
}
```

| Campo | Regra |
|---|---|
| `name` | obrigatório, ≤ 200 |
| `description` | opcional |
| `idCategory` | obrigatório, categoria ativa |
| `price` | obrigatório, ≥ 0, 2 casas (`CHECK (price >= 0)`) |
| `stock` | opcional, default 0, ≥ 0 |
| `photos` | opcional, ≤ 10, `position` único no array |

Todo produto é da loja: `id_owner` é preenchido com o `owner` único, nunca vem do corpo.

**Resposta `201`** — mesmo corpo de `GET /products/{id}`, com `Location`.

| Código | Quando |
|---|---|
| `201` | criado |
| `400` | campo obrigatório ausente; `price` negativo; `stock` negativo; > 10 fotos |
| `401` | token inválido |
| `403` | sem `catalog:write` ou não é `owner` |
| `404` | `idCategory` inexistente ou removida |
| `413` | payload acima de 1 MB |
| `415` | `Content-Type` |
| `422` | `price` com mais de 2 casas; `photoUrl` não é URL absoluta https |
| `429` | mais de 100 produtos criados em 1 h |
| `500` / `503` | banco |

---

#### `POST /products/{id}/photos` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `catalog:write` + papel `owner` |
| **Content-Type** | `multipart/form-data` (`file`) **ou** `application/json` (`photoUrl`) |

**Request (JSON)**

```json
{ "photoUrl": "https://cdn.loja.dev/p/118/3.webp", "position": 3 }
```

**Resposta `201`**

```json
{ "id": 904, "idProduct": 118, "photoUrl": "https://…", "position": 3 }
```

| Código | Quando |
|---|---|
| `201` | adicionada |
| `400` | nem `file` nem `photoUrl`; `position` negativa |
| `401` | token inválido |
| `403` | sem `catalog:write` |
| `404` | produto inexistente ou removido |
| `409` | limite de 10 fotos atingido |
| `413` | arquivo acima de 10 MB |
| `415` | tipo não é `image/jpeg`, `image/png` ou `image/webp` |
| `422` | imagem corrompida ou abaixo de 400×400 |
| `500` / `503` | banco ou storage |
| `502` | storage de objetos devolveu erro |

---

#### `POST /products/{id}/reviews` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `reviews:write` |

**Request**

```json
{ "idOrder": 3301, "rate": 5, "title": "Excelente", "description": "Digitação ótima." }
```

| Campo | Regra |
|---|---|
| `idOrder` | obrigatório — precisa existir em `review_eligibility (id_user, id_product, id_order)` |
| `rate` | obrigatório, 1–5 (`CHECK (rate BETWEEN 1 AND 5)`) |
| `title` | opcional, ≤ 150 |
| `description` | opcional |

A elegibilidade **não** é verificada chamando o `order`: ela já está materializada em
`review_eligibility`, preenchida pelo evento `order.delivered`. É o que permite avaliar sem
acoplamento síncrono entre serviços.

Efeito colateral: recalcula `product.rating` e `product.rating_count` na mesma transação e
invalida `catalog:product:{id}`.

| Código | Quando |
|---|---|
| `201` | criada, `Location: /reviews/501` |
| `400` | `rate` ausente ou fora de 1–5; `idOrder` ausente |
| `401` | token inválido |
| `403` | **sem elegibilidade** — não comprou, ou o pedido não foi entregue |
| `404` | produto inexistente |
| `409` | já avaliou esse produto nesse pedido (`review_user_product_order_uk`) |
| `415` | `Content-Type` |
| `422` | texto reprovado na moderação |
| `429` | mais de 10 avaliações em 1 h |
| `500` / `503` | banco |

> Aqui `403` é correto e `404` seria errado: o produto existe e é público, o que falta é
> direito de escrever. Não há informação a esconder.

---

#### `POST /internal/reservations/{orderId}/confirm` — `PLANEJADO`

Caminho de contingência. O normal é o evento `order.paid` levar a reserva de `held` para
`confirmed` e debitar `product.stock` (`docs/event-contracts.md` §4.3).

| | |
|---|---|
| **Auth** | `internal:hydrate` |

| Código | Quando |
|---|---|
| `200` | confirmada (ou já estava — idempotente) |
| `401` / `403` | token / escopo |
| `404` | não há reserva para o pedido |
| `409` | reserva já `released`, ou vencida (`expires_at` passou) e o estoque não está mais garantido |
| `500` / `503` | banco |

---

#### `POST /internal/reservations/{orderId}/release` — `PLANEJADO`

Contingência do `ReleaseReservationService`, hoje disparado por `order.cancelled`.

| Código | Quando |
|---|---|
| `200` | liberada (ou já estava) |
| `401` / `403` | token / escopo |
| `404` | não há reserva |
| `409` | reserva já `confirmed` — estoque debitado, o caminho correto é estorno |
| `500` / `503` | banco |

### 7.3 PUT

#### `PUT /products/{id}` — `PLANEJADO`

Substituição completa. Mesmo corpo do `POST /products`, sem `photos`.

| Código | Quando |
|---|---|
| `200` | substituído |
| `400` | campo obrigatório ausente |
| `401` | token inválido |
| `403` | sem `catalog:write` |
| `404` | inexistente ou removido |
| `409` | há reserva `held` ativa e o `stock` novo é menor que o já reservado |
| `412` | `If-Match` com `ETag` velho (edição concorrente) |
| `415` | `Content-Type` |
| `422` | `price` inválido |
| `500` / `503` | banco |

---

#### `PUT /products/{id}/photos/order` — `PLANEJADO`

Reordena em bloco.

**Request**

```json
{ "order": [ { "id": 901, "position": 0 }, { "id": 904, "position": 1 } ] }
```

| Código | Quando |
|---|---|
| `200` | reordenado, devolve a lista final |
| `400` | array vazio; `position` repetida |
| `401` / `403` | token / escopo |
| `404` | produto inexistente, ou algum `id` de foto não pertence a ele |
| `422` | a lista não cobre todas as fotos do produto |
| `500` / `503` | banco |

### 7.4 PATCH

#### `PATCH /products/{id}` — `PLANEJADO`

Todos os campos opcionais; ao menos um. Invalida `catalog:product:{id}` e
`catalog:hydrate:{id}`.

**Request**

```json
{ "price": "329.90", "description": "Nova descrição." }
```

| Código | Quando |
|---|---|
| `200` | atualizado |
| `400` | corpo vazio; tentativa de alterar `rating` ou `ratingCount` |
| `401` | token inválido |
| `403` | sem `catalog:write` |
| `404` | inexistente ou removido |
| `409` | produto tem reserva `held` e a mudança afeta preço de pedido em curso |
| `412` | `If-Match` desatualizado |
| `415` | `Content-Type` |
| `422` | `price` negativo ou com mais de 2 casas |
| `500` / `503` | banco |

> Mudar preço **não** altera pedido já criado: `order_item.price_at_time` é snapshot.

---

#### `PATCH /products/{id}/stock` — `PLANEJADO`

**Request** — exatamente uma das duas formas:

```json
{ "stock": 20 }
```

```json
{ "delta": 5 }
```

`delta` é aplicado com `UPDATE … SET stock = stock + :delta` e resolve a concorrência no banco.
`stock` absoluto aceita `If-Match` para *optimistic locking*.

**Resposta `200`**

```json
{ "idProduct": 118, "stock": 20, "held": 3, "available": 17 }
```

| Código | Quando |
|---|---|
| `200` | atualizado |
| `400` | nenhum dos dois campos, ou os dois juntos |
| `401` | token inválido |
| `403` | sem `catalog:write` |
| `404` | inexistente ou removido |
| `409` | resultado ficaria negativo (`CHECK (stock >= 0)`), ou abaixo do já reservado em `held` |
| `412` | `If-Match` desatualizado |
| `415` | `Content-Type` |
| `422` | `stock`/`delta` não inteiro |
| `500` / `503` | banco |

---

#### `PATCH /reviews/{id}` — `PLANEJADO`

Só `rate`, `title` e `description`, e apenas pelo autor. Recalcula o rating do produto.

| Código | Quando |
|---|---|
| `200` | atualizada, com `editedAt` preenchido |
| `400` | corpo vazio; `rate` fora de 1–5 |
| `401` | token inválido |
| `403` | sem `reviews:write` |
| `404` | inexistente, removida ou de outro autor |
| `409` | janela de edição de 30 dias expirada |
| `415` | `Content-Type` |
| `422` | texto reprovado na moderação |
| `500` / `503` | banco |

### 7.5 DELETE

#### `DELETE /products/{id}` — `PLANEJADO`

Soft delete. Some da vitrine; continua visível em pedidos antigos (que têm snapshot próprio).

| Código | Quando |
|---|---|
| `204` | removido |
| `401` | token inválido |
| `403` | sem `catalog:write` |
| `404` | inexistente ou já removido |
| `409` | há reserva `held` ativa — o pedido em curso precisa fechar ou cair antes |
| `500` / `503` | banco |

---

#### `DELETE /products/{id}/photos/{photoId}` — `PLANEJADO`

| Código | Quando |
|---|---|
| `204` | removida |
| `401` / `403` | token / escopo |
| `404` | produto ou foto inexistente, ou foto de outro produto |
| `409` | é a única foto e o produto está ativo |
| `500` / `503` | banco |

---

#### `DELETE /reviews/{id}` — `PLANEJADO`

Só o autor. Recalcula o rating.

| Código | Quando |
|---|---|
| `204` | removida |
| `401` / `403` | token / escopo |
| `404` | inexistente, já removida ou de outro autor |
| `500` / `503` | banco |

---

## 8. Serviço `order` `:8083`

Dono de `cart`, `cart_items`, `orders` e `order_item`. Orquestra a saga de checkout.

**Invariante do agregado `Order`:** `itemsCost = Σ(priceAtTime × quantity)` e
`totalCost = itemsCost + freightCost`. Item de pedido nunca é gravado fora do pedido.

**`cart_items` não tem preço** de propósito: o carrinho hidrata do `inventory` na leitura e o
preço é **revalidado no checkout**. É isso que impede que um preço visto há três dias vire
pedido hoje.

### 8.1 GET

#### `GET /cart` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `cart:read` |
| **Cache** | nenhum para o carrinho; `catalog:hydrate:{id}` (60 s) para os produtos |

**Resposta `200`**

```json
{
  "id": 88,
  "items": [
    {
      "id": 210,
      "idProduct": 118,
      "quantity": 2,
      "product": {
        "name": "Teclado mecânico ABNT2",
        "price": "349.90",
        "photoUrl": "https://cdn.loja.dev/p/118/0.webp",
        "available": 12,
        "active": true
      },
      "lineTotal": "699.80",
      "issues": []
    }
  ],
  "itemsCost": "699.80",
  "issues": [],
  "updatedAt": "2026-09-17T13:58:00Z"
}
```

`issues[]` por item, quando aplicável:

| Código | Significado |
|---|---|
| `PRODUCT_UNAVAILABLE` | produto removido (`active: false`) |
| `INSUFFICIENT_STOCK` | `quantity` > `available` |
| `PRICE_CHANGED` | preço mudou desde a última leitura do cliente |

O carrinho **não** se conserta sozinho: devolve `200` com os problemas marcados, e o cliente
decide. Remover item silenciosamente é a pior experiência possível no checkout.

| Código | Quando |
|---|---|
| `200` | ok — carrinho vazio também é `200`, com `items: []` |
| `401` / `403` | token / escopo |
| `500` / `503` | banco |
| `504` | timeout ao hidratar no `inventory` — devolve os itens sem `product`, com `issues: ["HYDRATION_TIMEOUT"]` |

---

#### `GET /orders` — `PLANEJADO`

Pedidos do usuário do token.

| | |
|---|---|
| **Auth** | `orders:read` |
| **Query** | `status` (repetível), `from`/`to` (ISO date), `page`, `size`, `sort` (default `createdAt,desc`) |

**Resposta `200`**

```json
{
  "content": [
    {
      "id": 3301,
      "status": "paid",
      "itemsCost": "699.80",
      "freightCost": "25.00",
      "totalCost": "724.80",
      "itemCount": 2,
      "firstItem": {
        "productName": "Teclado mecânico ABNT2",
        "productPhotoUrl": "https://cdn.loja.dev/p/118/0.webp"
      },
      "createdAt": "2026-09-17T14:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 7, "totalPages": 1 }
}
```

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `status` fora do enum; `from` > `to`; paginação inválida |
| `401` / `403` | token / escopo |
| `500` / `503` | banco |

---

#### `GET /orders/{id}` — `PLANEJADO`

**Resposta `200`**

```json
{
  "id": 3301,
  "status": "paid",
  "idAddress": 15,
  "items": [
    {
      "id": 7701,
      "idProduct": 118,
      "productName": "Teclado mecânico ABNT2",
      "productPhotoUrl": "https://cdn.loja.dev/p/118/0.webp",
      "priceAtTime": "349.90",
      "quantity": 2,
      "lineTotal": "699.80"
    }
  ],
  "itemsCost": "699.80",
  "freightCost": "25.00",
  "totalCost": "724.80",
  "payment": { "id": 9901, "status": "captured", "provider": "mercadopago" },
  "shipment": { "id": 5501, "status": "ready_to_ship", "trackingCode": null },
  "createdAt": "2026-09-17T14:00:00Z",
  "updatedAt": "2026-09-17T14:06:31Z"
}
```

`payment` e `shipment` são projeções: o `order` as mantém a partir dos eventos recebidos, sem
chamada síncrona no caminho de leitura. São `null` enquanto o evento não chegou.

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `id` não numérico |
| `401` / `403` | token / escopo |
| `404` | inexistente **ou de outro cliente** |
| `500` / `503` | banco |

---

#### `GET /orders/manage` — `PLANEJADO`

Todos os pedidos da loja, para gestão.

| | |
|---|---|
| **Auth** | `sales:read` + papel `owner` |
| **Query** | `status` (repetível), `customerId`, `from`/`to`, `page`, `size`, `sort` (default `createdAt,desc`) |

**Resposta `200`** — mesmo formato de `GET /orders`, com `idCustomer` em cada linha. O nome do
cliente o front busca em `GET /users/{id}`: o pedido não guarda snapshot do cliente.

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `status` fora do enum; `from` > `to`; paginação inválida |
| `401` | token inválido |
| `403` | sem `sales:read` ou não é `owner` |
| `500` / `503` | banco |

---

#### `GET /internal/orders/{id}` — `PLANEJADO`

Leitura servidor-a-servidor (`payment` valida valor, `shipment` monta o envio).

**Resposta `200`** — pedido completo, com `idCustomer` e sem projeção de pagamento/envio.

| Código | Quando |
|---|---|
| `200` | ok |
| `401` / `403` | token / escopo `internal:hydrate` |
| `404` | inexistente |
| `500` / `503` | banco |

### 8.2 POST

#### `POST /cart/items` — `PLANEJADO`

Adiciona ou **soma** à quantidade existente (`cart_items_cart_product_uk` garante uma linha por
produto). Cria o carrinho no primeiro item.

| | |
|---|---|
| **Auth** | `cart:write` |

**Request**

```json
{ "idProduct": 118, "quantity": 2 }
```

`quantity` ≥ 1 (`CHECK (quantity > 0)`).

**Resposta `201`** — carrinho completo, igual ao `GET /cart`.

| Código | Quando |
|---|---|
| `201` | item adicionado (ou somado) |
| `400` | `idProduct` ausente; `quantity` ≤ 0 ou não inteiro |
| `401` / `403` | token / escopo |
| `404` | produto inexistente ou removido no `inventory` |
| `409` | quantidade resultante acima do disponível |
| `413` | carrinho já tem 50 linhas distintas |
| `415` | `Content-Type` |
| `422` | `quantity` acima do limite por item (99) |
| `500` / `503` | banco |
| `504` | timeout ao validar no `inventory` |

---

#### `POST /orders` — `PLANEJADO`

**Checkout.** Rota mais crítica do sistema. Fecha o carrinho inteiro num pedido só.

| | |
|---|---|
| **Auth** | `orders:write` |
| **Headers** | `Idempotency-Key` **obrigatório** |

**Request**

```json
{
  "addressId": 15,
  "expectedTotalCost": "724.80"
}
```

| Campo | Regra |
|---|---|
| `addressId` | obrigatório, endereço do próprio usuário — gravado em `orders.id_address` |
| `expectedTotalCost` | opcional; o total que o cliente viu na tela. Divergente do recalculado ⇒ `409`, nada é criado |

Frete **não** vem do cliente: é sempre calculado pelo servidor (§10.1). Frete informado pelo
cliente é frete escolhido pelo cliente.

**Sequência:**

1. Lê o carrinho; vazio ⇒ `422`.
2. Revalida no `inventory` (`GET /internal/products`): existência, preço e disponibilidade.
3. Confere o endereço: `GET /internal/addresses/{addressId}?userId={sub}` no `user`.
4. Calcula o frete: `GET /shipping/quote?zipcode=` no `shipment`.
5. `expectedTotalCost` divergente ⇒ `409 PRICE_CHANGED`, sem criar nada.
6. Grava `orders` (com `id_address`) + `order_item` com os **snapshots** (`product_name`,
   `product_photo_url`, `price_at_time`), status `pending`.
7. Esvazia o carrinho.
8. Publica `ecommerce.order.created.v1`.

A reserva de estoque é **assíncrona**: o `inventory` consome `order.created`, tenta reservar e
responde `stock.reserved` ou `stock.rejected`. Um `stock.rejected` cancela o pedido
automaticamente (`StockEventsConsumer` → `CancelOrderService`). Por isso o `201` significa
"pedido registrado", não "estoque garantido" — e o corpo diz isso explicitamente.

**Resposta `201`**

```json
{
  "id": 3301,
  "status": "pending",
  "stockReservation": "pending",
  "idAddress": 15,
  "itemsCost": "699.80",
  "freightCost": "25.00",
  "totalCost": "724.80",
  "items": [ { "…": "…" } ],
  "nextStep": { "action": "CREATE_PAYMENT", "href": "/api/v1/payments" },
  "createdAt": "2026-09-17T14:00:00Z"
}
```

| Código | Quando |
|---|---|
| `201` | pedido criado em `pending` |
| `400` | `addressId` ausente; `Idempotency-Key` ausente ou não-UUID |
| `401` | token inválido |
| `403` | sem `orders:write` |
| `404` | `addressId` não é do usuário; algum produto do carrinho não existe mais |
| `409` | `PRICE_CHANGED`; `INSUFFICIENT_STOCK` na revalidação; `IDEMPOTENCY_IN_FLIGHT` |
| `415` | `Content-Type` |
| `422` | carrinho vazio; item com `active: false`; `ZIPCODE_NOT_GEOCODED`; `IDEMPOTENCY_KEY_REUSED` |
| `429` | mais de 10 checkouts em 5 min |
| `500` | falha ao gravar |
| `502` | `inventory`, `user` ou `shipment` com resposta inválida |
| `503` | `inventory` indisponível — **o pedido não é criado**, porque sem revalidação de preço não há como garantir o valor |
| `504` | timeout em alguma das consultas |

---

#### `POST /orders/{id}/cancel` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `orders:write` (cliente) ou `sales:read` + papel `owner` (loja) |

**Request**

```json
{ "reason": "Comprei por engano" }
```

Publica `order.cancelled` — o `inventory` libera ou devolve o estoque e o `shipment` cancela o
envio — e, se havia pagamento, `order.refund.requested`. O estorno é **assíncrono**: a resposta
sai com `cancelled`, e o pedido vai para `refunded` quando o `payment.refunded` chegar
(`docs/event-contracts.md` §10.6). Estorno que falha vai para a DLT e a loja estorna à mão.

**Transições permitidas:** `pending` → `cancelled`, `paid` → `cancelled` (com estorno),
`processing` → `cancelled` (só o `owner`). De `shipped` em diante, o caminho é devolução, não
cancelamento.

| Código | Quando |
|---|---|
| `200` | cancelado, devolve o pedido com `status: "cancelled"` |
| `400` | `reason` acima de 500 caracteres |
| `401` | token inválido |
| `403` | não é o cliente do pedido nem o `owner` |
| `404` | inexistente ou de outro cliente |
| `409` | já `cancelled`/`refunded`; status não permite (`shipped`, `delivered`) |
| `415` | `Content-Type` |
| `500` / `503` | banco |

### 8.3 PUT

#### `PUT /cart/items/{idProduct}` — `PLANEJADO`

Define a quantidade (idempotente), em vez de somar como o `POST`.

**Request**

```json
{ "quantity": 3 }
```

| Código | Quando |
|---|---|
| `200` | quantidade definida, devolve o carrinho |
| `400` | `quantity` ausente, ≤ 0 ou não inteiro |
| `401` / `403` | token / escopo `cart:write` |
| `404` | item não está no carrinho |
| `409` | acima do disponível |
| `415` | `Content-Type` |
| `422` | acima de 99 |
| `500` / `503` | banco |
| `504` | timeout no `inventory` |

### 8.4 PATCH

#### `PATCH /internal/orders/{id}/status` — `PLANEJADO`

Contingência de reconciliação. O caminho normal de mudança de status é **evento**, nunca HTTP.

| | |
|---|---|
| **Auth** | `internal:hydrate` |

**Request**

```json
{ "status": "paid", "reason": "reconciliação manual do pagamento 9901" }
```

| Código | Quando |
|---|---|
| `200` | status alterado |
| `400` | `status` fora do enum `order_status` |
| `401` / `403` | token / escopo |
| `404` | pedido inexistente |
| `409` | transição inválida (ex.: `cancelled` → `paid`) |
| `422` | `reason` ausente — mudança manual sem justificativa não entra |
| `500` / `503` | banco |

### 8.5 DELETE

#### `DELETE /cart/items/{idProduct}` — `PLANEJADO`

| Código | Quando |
|---|---|
| `200` | removido, devolve o carrinho atualizado |
| `401` / `403` | token / escopo `cart:write` |
| `404` | item não está no carrinho |
| `500` / `503` | banco |

---

#### `DELETE /cart` — `PLANEJADO`

Esvazia (soft delete de todos os `cart_items`). O `cart` em si permanece.

| Código | Quando |
|---|---|
| `204` | esvaziado — **também quando já estava vazio**, porque a operação é idempotente |
| `401` / `403` | token / escopo |
| `500` / `503` | banco |

---

#### Sobre `DELETE /orders/{id}`

**Não existe.** Pedido é histórico contábil: `orders` e `order_item` não têm `deleted_at` por
decisão de modelagem. O encerramento é `POST /orders/{id}/cancel`. A rota responde `405`.

---

## 9. Serviço `payment` `:8084` — Mercado Pago

Dono de `payment`. Integra com o Mercado Pago em três modalidades.

### 9.1 Modalidades

| Modalidade | Como funciona | Campos MP relevantes |
|---|---|---|
| **Checkout Pro** | criamos uma *preference*, o cliente vai para o MP e volta | `init_point`, `sandbox_init_point`, `back_urls`, `notification_url` |
| **PIX** | pagamento direto, devolve QR Code | `payment_method_id: "pix"`, `point_of_interaction.transaction_data.{qr_code, qr_code_base64, ticket_url}` |
| **Cartão (transparente)** | o **front** tokeniza com o SDK do MP; o backend nunca vê o PAN | `token`, `payment_method_id`, `issuer_id`, `installments`, `payer.identification` |

> **O número do cartão nunca chega ao nosso backend.** O front usa o Bricks/SDK do Mercado Pago
> com a *public key* e envia só o `token` de uso único. É o que mantém o escopo de PCI-DSS fora
> desta aplicação. Nenhuma rota aqui aceita `card_number`, `cvv` ou `expiration`.

### 9.2 Mapeamento de status

| Status do MP | `payment_status` local | Efeito |
|---|---|---|
| `pending`, `in_process`, `in_mediation` | `pending` | nada |
| `authorized` | `authorized` | nada (aguarda captura) |
| `approved` | `captured` | publica `payment.approved` → pedido `paid` |
| `rejected` | `failed` | publica `payment.failed` |
| `cancelled` | `cancelled` | publica `payment.failed` |
| `refunded`, `charged_back` | `refunded` | publica `payment.refunded` → pedido `refunded` |

### 9.3 GET

#### `GET /payments/config` — `PLANEJADO`

Dados que o front precisa para inicializar o SDK do MP. Público.

**Resposta `200`**

```json
{
  "provider": "mercadopago",
  "publicKey": "APP_USR-1a2b3c4d-…",
  "locale": "pt-BR",
  "currency": "BRL",
  "environment": "sandbox",
  "enabledMethods": ["pix", "credit_card", "checkout_pro"]
}
```

Só a **public key**. O `access_token` do MP nunca sai do serviço.

| Código | Quando |
|---|---|
| `200` | ok (`Cache-Control: public, max-age=3600`) |
| `500` | `MP_PUBLIC_KEY` não configurada |
| `503` | serviço em shutdown |

---

#### `GET /payments/methods` — `PLANEJADO`

Espelho de `GET /v1/payment_methods` do MP: bandeiras aceitas, parcelamento, valores mínimos.

| | |
|---|---|
| **Auth** | `payments:read` |
| **Query** | `amount` (opcional — calcula parcelas) |
| **Cache** | `payment:methods:mp`, 6 h |

**Resposta `200`**

```json
{
  "methods": [
    {
      "id": "pix",
      "name": "PIX",
      "type": "bank_transfer",
      "thumbnail": "https://http2.mlstatic.com/…/pix.gif",
      "minAmount": "0.01",
      "maxAmount": "50000.00"
    },
    {
      "id": "master",
      "name": "Mastercard",
      "type": "credit_card",
      "installments": [
        { "quantity": 1, "amount": "724.80", "totalAmount": "724.80", "interestFree": true },
        { "quantity": 3, "amount": "241.60", "totalAmount": "724.80", "interestFree": true }
      ]
    }
  ]
}
```

| Código | Quando |
|---|---|
| `200` | ok (cache ou MP) |
| `400` | `amount` não decimal |
| `401` / `403` | token / escopo |
| `500` | credencial do MP ausente |
| `502` | MP devolveu payload inesperado |
| `503` | MP fora **e** cache frio |
| `504` | timeout no MP (limite 5 s) |

---

#### `GET /payments/{id}` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `payments:read` |

**Resposta `200`**

```json
{
  "id": 9901,
  "idOrder": 3301,
  "value": "724.80",
  "status": "captured",
  "provider": "mercadopago",
  "externalId": "119384756201",
  "method": "pix",
  "detail": {
    "qrCode": "00020126580014br.gov.bcb.pix…",
    "qrCodeBase64": "iVBORw0KGgoAAAANS…",
    "ticketUrl": "https://www.mercadopago.com.br/payments/119384756201/ticket?…",
    "expiresAt": "2026-09-17T14:33:11Z"
  },
  "statusDetail": "accredited",
  "createdAt": "2026-09-17T14:03:11Z",
  "updatedAt": "2026-09-17T14:06:31Z"
}
```

`detail` varia por modalidade: QR no PIX, `initPoint` no Checkout Pro,
`{last4, brand, installments}` no cartão. `idempotencyKey` **não** é exposto.

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `id` não numérico |
| `401` / `403` | token / escopo |
| `404` | inexistente, removido, ou de pedido de outro cliente |
| `500` / `503` | banco |

---

#### `GET /payments?orderId={id}` — `PLANEJADO`

Todas as tentativas de um pedido (retry após recusa gera nova linha).

| Código | Quando |
|---|---|
| `200` | ok, inclusive lista vazia |
| `400` | `orderId` ausente ou não numérico |
| `401` / `403` | token / escopo |
| `404` | pedido inexistente ou de outro cliente |
| `500` / `503` | banco |

---

#### `GET /internal/payments?orderId={id}` — `PLANEJADO`

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `orderId` ausente |
| `401` / `403` | token / escopo `internal:hydrate` |
| `500` / `503` | banco |

### 9.4 POST

#### `POST /payments` — `PLANEJADO`

Cria a cobrança no Mercado Pago.

| | |
|---|---|
| **Auth** | `payments:write` |
| **Headers** | `Idempotency-Key` **obrigatório** |

**Request — Checkout Pro**

```json
{ "idOrder": 3301, "method": "checkout_pro" }
```

**Request — PIX**

```json
{
  "idOrder": 3301,
  "method": "pix",
  "payer": { "email": "tiago@exemplo.com", "identification": { "type": "CPF", "number": "12345678901" } }
}
```

**Request — Cartão**

```json
{
  "idOrder": 3301,
  "method": "credit_card",
  "token": "ff8080814c11e237014c1ff593b57b4d",
  "paymentMethodId": "master",
  "issuerId": "24",
  "installments": 3,
  "payer": { "email": "tiago@exemplo.com", "identification": { "type": "CPF", "number": "12345678901" } }
}
```

| Campo | Regra |
|---|---|
| `idOrder` | obrigatório, pedido do próprio usuário em `pending` |
| `method` | obrigatório: `checkout_pro` \| `pix` \| `credit_card` |
| `token` | obrigatório em `credit_card`, uso único |
| `installments` | obrigatório em `credit_card`, ≥ 1 |
| `payer.email` | obrigatório em `pix` e `credit_card` |

O **valor não vem do cliente**: é lido de `GET /internal/orders/{id}` (`totalCost`). Aceitar
valor do corpo seria deixar o cliente escolher quanto pagar.

O `Idempotency-Key` vira `payment.idempotency_key` (`UNIQUE`) e vai ao MP no header
`X-Idempotency-Key`.

**Resposta `201` — PIX**

```json
{
  "id": 9901,
  "idOrder": 3301,
  "value": "724.80",
  "status": "pending",
  "method": "pix",
  "externalId": "119384756201",
  "detail": {
    "qrCode": "00020126580014br.gov.bcb.pix…",
    "qrCodeBase64": "iVBORw0KGgoAAAANS…",
    "ticketUrl": "https://www.mercadopago.com.br/payments/119384756201/ticket?…",
    "expiresAt": "2026-09-17T14:33:11Z"
  },
  "createdAt": "2026-09-17T14:03:11Z"
}
```

**Resposta `201` — Checkout Pro**

```json
{
  "id": 9902,
  "idOrder": 3301,
  "value": "724.80",
  "status": "pending",
  "method": "checkout_pro",
  "externalId": "1234567890-a1b2c3d4-…",
  "detail": {
    "initPoint": "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=…",
    "expiresAt": "2026-09-18T14:03:11Z"
  }
}
```

**Resposta `201` — cartão aprovado**

```json
{
  "id": 9903,
  "idOrder": 3301,
  "value": "724.80",
  "status": "captured",
  "method": "credit_card",
  "externalId": "119384756202",
  "statusDetail": "accredited",
  "detail": { "brand": "master", "last4": "4321", "installments": 3 }
}
```

> **Cartão recusado devolve `201`, não `4xx`.** A requisição foi processada com sucesso; o que
> foi recusado foi a cobrança. O `status: "failed"` e o `statusDetail` (`cc_rejected_call_for_authorize`,
> `cc_rejected_insufficient_amount`, `cc_rejected_bad_filled_security_code`…) dizem ao front o
> que exibir. Devolver `402` aqui obrigaria o cliente a tratar erro HTTP para um evento de
> negócio normal, e perderia o `statusDetail`.

| Código | Quando |
|---|---|
| `201` | pagamento criado no MP — **inclusive recusado**, com `status: "failed"` |
| `400` | `idOrder` ausente; `method` fora do enum; `token` ausente em `credit_card`; `Idempotency-Key` ausente |
| `401` | token inválido |
| `403` | sem `payments:write`, ou o pedido é de outro cliente |
| `404` | pedido inexistente |
| `409` | pedido já tem pagamento `captured`/`authorized`; pedido não está em `pending`; `IDEMPOTENCY_IN_FLIGHT` |
| `410` | `token` de cartão expirado (validade ~7 dias no MP) |
| `415` | `Content-Type` |
| `422` | pedido `cancelled`; valor abaixo do mínimo do método; `installments` não oferecido; CPF inválido; `IDEMPOTENCY_KEY_REUSED` |
| `429` | rate limit do MP repassado, ou 5 tentativas em 10 min no mesmo pedido |
| `500` | falha ao gravar após criar no MP — **cobrança órfã**, reconciliada por `POST /payments/{id}/sync` |
| `502` | MP devolveu resposta não interpretável |
| `503` | MP indisponível |
| `504` | timeout no MP (limite 15 s) — o pagamento pode ter sido criado; o retry com a mesma `Idempotency-Key` resolve |

---

#### `POST /payments/{id}/refund` — `PLANEJADO`

Estorno total ou parcial.

| | |
|---|---|
| **Auth** | `payments:refund` — `owner` |
| **Headers** | `Idempotency-Key` **obrigatório** |

**Request**

```json
{ "amount": "724.80", "reason": "Produto indisponível no estoque físico" }
```

`amount` omitido ⇒ estorno total.

**Resposta `200`**

```json
{
  "id": 9901,
  "status": "refunded",
  "refunded": { "amount": "724.80", "externalRefundId": "1290384756", "at": "2026-09-18T10:00:00Z" }
}
```

| Código | Quando |
|---|---|
| `200` | estornado |
| `202` | estorno aceito e em processamento no MP (comum em PIX e boleto) |
| `400` | `amount` não decimal ou ≤ 0; `Idempotency-Key` ausente |
| `401` | token inválido |
| `403` | sem `payments:refund` (não é o `owner`) |
| `404` | pagamento inexistente |
| `409` | já `refunded`; `amount` acima do saldo estornável; `IDEMPOTENCY_IN_FLIGHT` |
| `415` | `Content-Type` |
| `422` | status não permite estorno (`pending`, `failed`, `cancelled`); fora da janela do MP (180 dias) |
| `429` | rate limit |
| `500` | falha ao gravar |
| `502` / `503` / `504` | MP |

---

#### `POST /payments/{id}/sync` — `PLANEJADO`

Reconciliação: relê o pagamento no MP (`GET /v1/payments/{externalId}`) e reaplica o mapeamento
de status. Existe para os casos de webhook perdido e de `500`/`504` no `POST /payments`.

| | |
|---|---|
| **Auth** | `payments:read` (cliente do pedido) ou `payments:refund` (`owner`) |

| Código | Quando |
|---|---|
| `200` | sincronizado, devolve o pagamento (com `changed: true|false`) |
| `401` / `403` | token / escopo |
| `404` | pagamento inexistente, ou sem `external_id` (nunca chegou ao MP) |
| `409` | status local à frente do remoto — não regride |
| `429` | mais de 1 sync por minuto no mesmo pagamento |
| `500` / `502` / `503` / `504` | MP |

---

#### `POST /payments/{id}/cancel` — `PLANEJADO`

Cancela cobrança ainda não paga (PIX não pago, preference aberta).

| Código | Quando |
|---|---|
| `200` | cancelado |
| `401` / `403` | token / escopo `payments:write` |
| `404` | inexistente ou de outro cliente |
| `409` | já `captured` — o caminho é estorno, não cancelamento |
| `422` | status não permite (`failed`, `refunded`, `cancelled`) |
| `500` / `502` / `503` / `504` | MP |

---

#### `POST /webhooks/mercadopago` — `PLANEJADO`

Recebe o repasse do gateway (§5.2). **Não é acessível de fora**: exige token interno com
`webhooks:ingest`.

**Request** (corpo original do MP + contexto da validação feita na borda)

```json
{
  "signatureVerified": true,
  "receivedAt": "2026-09-17T14:06:30Z",
  "payload": {
    "id": 12345678901,
    "type": "payment",
    "action": "payment.updated",
    "data": { "id": "119384756201" }
  }
}
```

**Processamento:**

1. Dedupe por `payment:webhook:{payload.id}` no Redis (TTL 24 h) — o MP reenvia o mesmo evento.
2. Busca a verdade em `GET /v1/payments/{data.id}` no MP. **O corpo do webhook nunca é fonte de
   verdade**: ele só diz *qual* pagamento olhar. Confiar no status que vem no webhook é aceitar
   o status que quem chamou quiser mandar.
3. Aplica o mapeamento (§9.2) e publica o evento Kafka correspondente.

| Código | Quando |
|---|---|
| `200` | processado, ou duplicado descartado |
| `400` | `payload.data.id` ausente |
| `401` / `403` | token / escopo `webhooks:ingest` |
| `404` | `external_id` não corresponde a pagamento nosso (evento de outra aplicação MP) |
| `409` | status local já à frente (chegada fora de ordem) — registrado e ignorado |
| `422` | `type` não tratado |
| `500` | falha ao gravar ou publicar — **propaga para o gateway devolver `5xx` ao MP e provocar retry** |
| `502` / `503` / `504` | MP fora ao buscar a verdade — idem, provoca retry |

### 9.5 PATCH / PUT / DELETE

**Não existem.** Pagamento é registro financeiro imutável; toda transição é efeito de webhook,
sync ou estorno. Essas rotas respondem `405 Method Not Allowed`.

---

## 10. Serviço `shipment` `:8085`

Dono de `shipment`. Guarda **snapshot achatado** de destino (`to_*`) e origem (`from_*`): o
endereço do cliente no `user` é mutável e a remessa não pode mudar de destino retroativamente.

**Sem transportadora integrada.** Não há cotação real nem rastreio automático: o frete é por
distância e o status avança pelas mãos da loja (despacho) e do comprador (recebimento).

### 10.1 Regra de frete

```
frete     = ceil(distância em km) × R$ 1,00
distância = haversine(coordenada da loja, coordenada do CEP de destino)
```

| Item | Decisão |
|---|---|
| Origem | endereço da loja em configuração, **já com latitude e longitude** — a origem nunca é geocodificada |
| Destino | CEP do endereço escolhido pelo comprador |
| Coordenada do destino | BrasilAPI `GET https://brasilapi.com.br/api/cep/v2/{cep}` → `location.coordinates`; cache `geo:cep:{cep}` por 30 d |
| Distância | linha reta (haversine). Subestima a distância por estrada — aceito como simplificação |
| Arredondamento | km inteiro para cima. CEP da própria loja ⇒ 0 km ⇒ frete `0.00` |
| Tarifa | `app.shipping.rate-per-km`, em configuração, não fixa no código |

```properties
# Endereco de origem de todo envio. Copiado para from_* quando o envio nasce:
# mudar aqui nao altera envio antigo.
app.store.origin.zipcode=07010000
app.store.origin.country=BR
app.store.origin.state=SP
app.store.origin.city=Guarulhos
app.store.origin.street=Rua Exemplo
app.store.origin.number=100
app.store.origin.latitude=-23.4628
app.store.origin.longitude=-46.5333

app.shipping.rate-per-km=1.00
```

O frete depende só do CEP de destino, então **nada de cotação é guardado**: a vitrine calcula
para exibir, o checkout recalcula com a mesma função, e o valor que vale é o gravado em
`orders.freight_cost`. O `shipment` recebe esse valor pronto no evento `order.confirmed` e o copia
para `freight_tax` — não recalcula, para não divergir se a tarifa mudou entre o checkout e o
pagamento.

### 10.2 Ciclo de status

| De | Para | Quem | Rota |
|---|---|---|---|
| — | `pending` | sistema, ao consumir `order.confirmed` | — |
| `pending` | `ready_to_ship` | loja (`owner`) | `PATCH /shipments/{id}` |
| `ready_to_ship` | `in_transit` | loja | `PATCH /shipments/{id}` |
| `in_transit` | `out_for_delivery` | loja (opcional) | `PATCH /shipments/{id}` |
| `in_transit`, `out_for_delivery` | `delivered` | **comprador** | `POST /shipments/{id}/confirm-delivery` |
| `in_transit`, `out_for_delivery` | `returned` | loja | `PATCH /shipments/{id}` |
| `pending`, `ready_to_ship` | `cancelled` | loja | `POST /shipments/{id}/cancel` |

`delivered` é declaração do **comprador**: é ela que encerra o pedido e libera a avaliação
(`order.delivered` → `review_eligibility`). Se a loja pudesse marcar entregue, fecharia o
pedido de quem não recebeu nada. Comprador que nunca confirma é pendência registrada em §13.2.

Toda transição publica `ecommerce.shipment.status.changed.v1`.

### 10.3 GET

#### `GET /shipping/quote?zipcode={cep}` — `PLANEJADO`

Frete para um CEP de destino. Público na borda: o cliente vê o frete antes de logar.

| | |
|---|---|
| **Auth** | `shipments:read` (o gateway emite mesmo para anônimo) |
| **Query** | `zipcode` — obrigatório, 8 dígitos sem máscara |
| **Cache** | `geo:cep:{cep}` (30 d) · `Cache-Control: public, max-age=3600` |

**Resposta `200`**

```json
{
  "zipcode": "01310100",
  "origin": { "city": "Guarulhos", "state": "SP" },
  "distanceKm": 25,
  "ratePerKm": "1.00",
  "freightCost": "25.00"
}
```

O `order` chama esta mesma rota no checkout, com token interno. Não existe rota interna
separada: o cálculo não depende de quem pergunta nem do conteúdo do carrinho.

| Código | Quando |
|---|---|
| `200` | ok |
| `304` | `ETag` |
| `400` | `zipcode` ausente ou sem 8 dígitos |
| `401` / `403` | token interno |
| `422` | `ZIPCODE_NOT_GEOCODED` — CEP sem coordenada na BrasilAPI |
| `429` | mais de 30 consultas/min por IP |
| `500` | falha não prevista; origem da loja não configurada |
| `502` | BrasilAPI devolveu payload inválido |
| `503` | BrasilAPI fora **e** CEP fora do cache |
| `504` | timeout na BrasilAPI (limite 5 s) |

---

#### `GET /shipments` — `PLANEJADO`

Envios do comprador do token.

| | |
|---|---|
| **Auth** | `shipments:read` |
| **Query** | `status` (repetível), `orderId`, `page`, `size`, `sort` |

**Resposta `200`**

```json
{
  "content": [
    {
      "id": 5501,
      "idOrder": 3301,
      "status": "in_transit",
      "freightTax": "25.00",
      "trackingCode": "AA123456789BR",
      "destination": { "city": "São Paulo", "state": "SP", "zipcode": "01310100" },
      "updatedAt": "2026-09-19T08:11:00Z"
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
}
```

`trackingCode` é texto livre informado pela loja, `null` até o despacho.

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `status` fora do enum `shipment_status`; paginação inválida |
| `401` / `403` | token / escopo |
| `500` / `503` | banco |

---

#### `GET /shipments/{id}` — `PLANEJADO`

Traz `destination` e `origin` completos (a origem é o endereço da loja).

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | `id` não numérico |
| `401` / `403` | token / escopo |
| `404` | inexistente, removido, ou quem chama não é o comprador do envio nem o `owner` |
| `500` / `503` | banco |

---

#### `GET /shipments/manage` — `PLANEJADO`

Todos os envios da loja, para gestão — em especial a fila do que falta despachar
(`status=pending&status=ready_to_ship`).

| | |
|---|---|
| **Auth** | `sales:read` + papel `owner` |
| **Query** | `status` (repetível), `orderId`, `page`, `size`, `sort` (default `createdAt,asc`) |

| Código | Quando |
|---|---|
| `200` | ok |
| `400` | filtro inválido |
| `401` | token inválido |
| `403` | sem `sales:read` ou não é `owner` |
| `500` / `503` | banco |

### 10.4 POST

#### `POST /shipments/{id}/confirm-delivery` — `PLANEJADO`

O comprador declara que recebeu.

| | |
|---|---|
| **Auth** | `shipments:write` + comprador do envio (`shipment.id_user = sub`) |
| **Corpo** | vazio |

Efeito: `delivered` → `shipment.status.changed` → pedido `delivered` → `order.delivered` →
`review_eligibility` no `inventory`.

| Código | Quando |
|---|---|
| `200` | confirmado — **também se já estava `delivered`**, a operação é idempotente |
| `401` | token inválido |
| `403` | sem `shipments:write` |
| `404` | inexistente, ou quem chama não é o comprador |
| `409` | status não permite: ainda não despachado (`pending`, `ready_to_ship`), `cancelled` ou `returned` |
| `500` / `503` | banco |

---

#### `POST /shipments/{id}/cancel` — `PLANEJADO`

| | |
|---|---|
| **Auth** | `shipments:write` + papel `owner` |

**Request**

```json
{ "reason": "Produto danificado antes do envio" }
```

| Código | Quando |
|---|---|
| `200` | cancelado |
| `400` | `reason` acima de 500 caracteres |
| `401` | token inválido |
| `403` | sem escopo ou não é o `owner` |
| `404` | inexistente |
| `409` | já `in_transit` ou além — o caminho é `returned` |
| `422` | já `cancelled` |
| `500` / `503` | banco |

---

#### `POST /internal/shipments` — `PLANEJADO`

Criação por contingência. O caminho normal é o evento `order.confirmed`.

**Request**

```json
{ "idOrder": 3301, "idUser": 42, "idAddressUser": 15, "freightTax": "25.00" }
```

Destino vem de `GET /internal/addresses/{idAddressUser}?userId={idUser}`; origem, da
configuração da loja.

| Código | Quando |
|---|---|
| `201` | envio criado em `pending` |
| `400` | campo obrigatório ausente |
| `401` / `403` | token / escopo `internal:hydrate` |
| `404` | endereço do comprador não encontrado |
| `409` | já existe envio para o pedido (`shipment_order_uk`) |
| `500` / `503` | banco; origem da loja não configurada |
| `504` | timeout ao buscar o endereço no `user` |

### 10.5 PATCH

#### `PATCH /shipments/{id}` — `PLANEJADO`

Como a loja avança o envio.

| | |
|---|---|
| **Auth** | `shipments:write` + papel `owner` |

**Request**

```json
{ "status": "in_transit", "trackingCode": "AA123456789BR" }
```

| Campo | Regra |
|---|---|
| `status` | obrigatório; só as transições da loja em §10.2 |
| `trackingCode` | opcional, texto livre ≤ 60 (`VARCHAR(60)`). Sem transportadora integrada, é só informação para o comprador |

| Código | Quando |
|---|---|
| `200` | atualizado |
| `400` | corpo vazio; `status` fora do enum; `trackingCode` acima de 60 |
| `401` | token inválido |
| `403` | sem `shipments:write`, não é o `owner`, ou tentou `delivered` (reservado ao comprador) |
| `404` | inexistente ou removido |
| `409` | transição inválida (ex.: `pending` → `in_transit` pulando `ready_to_ship`; qualquer saída de `delivered`) |
| `412` | `If-Match` desatualizado |
| `415` | `Content-Type` |
| `500` / `503` | banco |

### 10.6 PUT / DELETE

**Não existem.** Envio não é substituído nem apagado: avança por status ou é cancelado.
Respondem `405`.

---

## 11. Contrato de eventos Kafka

O contrato completo — payload, key, alias, efeito e resultados de consumo de cada evento, sagas
e política de retry/DLT — está em **`docs/event-contracts.md`**. Resumo:

| Tópico | Produtor | Consumidores | Status |
|---|---|---|---|
| `ecommerce.order.created.v1` | order | inventory | `IMPLEMENTADO` |
| `ecommerce.order.cancelled.v1` | order | inventory, shipment | `IMPLEMENTADO` (inventory) |
| `ecommerce.order.paid.v1` | order | inventory | `PLANEJADO` |
| `ecommerce.order.confirmed.v1` | order | shipment | `PLANEJADO` |
| `ecommerce.order.delivered.v1` | order | inventory | `PLANEJADO` |
| `ecommerce.order.refund.requested.v1` | order | payment | `PLANEJADO` |
| `ecommerce.stock.reserved.v1` | inventory | order | `IMPLEMENTADO` |
| `ecommerce.stock.rejected.v1` | inventory | order | `IMPLEMENTADO` |
| `ecommerce.stock.committed.v1` | inventory | order | `PLANEJADO` |
| `ecommerce.stock.commit.failed.v1` | inventory | order | `PLANEJADO` |
| `ecommerce.payment.approved.v1` | payment | order | `PLANEJADO` |
| `ecommerce.payment.failed.v1` | payment | order | `PLANEJADO` |
| `ecommerce.payment.refunded.v1` | payment | order | `PLANEJADO` |
| `ecommerce.shipment.status.changed.v1` | shipment | order | `PLANEJADO` |
| `ecommerce.user.deleted.v1` | user | inventory, order | `PLANEJADO` |

O `order` é o orquestrador: todo evento de outro serviço termina nele, e ele decide o passo
seguinte.

---

## 12. Fluxo ponta a ponta do checkout

```
1. COMPRADOR  GET /shipping/quote?zipcode= ─────────────────► SHIPMENT
              ◄── frete (R$ 1/km a partir da loja) ───────────┘

2. COMPRADOR  POST /orders {addressId} ─────────────────────► ORDER
                 ORDER ── GET /internal/products ───────────► INVENTORY   preço e estoque
                 ORDER ── GET /internal/addresses/{id} ─────► USER        endereço é do cliente?
                 ORDER ── GET /shipping/quote ──────────────► SHIPMENT    frete
                 ORDER grava pending (id_address + snapshots) e esvazia o carrinho
              ◄── 201 pending ────────────────────────────────┘
                 ORDER ══[order.created]════════════════════► INVENTORY   reserva held
                 ORDER ◄═[stock.reserved | stock.rejected]═══ INVENTORY   rejected ⇒ cancela

3. COMPRADOR  POST /payments ───────────────────────────────► PAYMENT ──► Mercado Pago
              ◄── 201 QR PIX / init_point / cartão ───────────┘

4. MERCADO PAGO ── webhook ──► GATEWAY (HMAC ok) ──────────► PAYMENT
                 PAYMENT ── GET /v1/payments/{id} ──────────► Mercado Pago   (fonte da verdade)
                 PAYMENT ══[payment.approved]═══════════════► ORDER       pending → paid
                 ORDER ══[order.paid]═══════════════════════► INVENTORY   confirma reserva, debita stock
                 ORDER ◄═[stock.committed]══════════════════ INVENTORY   paid → processing
                 ORDER ══[order.confirmed]══════════════════► SHIPMENT    cria envio pending
                                                                          (to_* do USER, from_* da config)

5. LOJA       PATCH /shipments/{id}  ready_to_ship → in_transit ► SHIPMENT
                 SHIPMENT ══[shipment.status.changed]═══════► ORDER       processing → shipped

6. COMPRADOR  POST /shipments/{id}/confirm-delivery ────────► SHIPMENT    delivered
                 SHIPMENT ══[shipment.status.changed]═══════► ORDER       shipped → delivered
                 ORDER ══[order.delivered]══════════════════► INVENTORY   grava review_eligibility

7. COMPRADOR  POST /products/{id}/reviews ──────────────────► INVENTORY   201
```

`───►` HTTP síncrono · `═══►` evento Kafka

### Pontos em que o fluxo quebra, e o que acontece

| Falha | Resultado |
|---|---|
| Estoque acabou entre o carrinho e o checkout | a revalidação do passo 2 devolve `409`; se acabar entre a gravação e a reserva, `stock.rejected` cancela o pedido com motivo |
| Cliente não paga o PIX | a reserva vence sozinha (`expires_at`); nenhuma rotina devolve estoque, a reserva só para de contar. O pedido fica `pending` até o TTL do checkout e então é cancelado |
| Webhook do MP se perde | `POST /payments/{id}/sync` reconcilia; um job varre pagamentos `pending` com mais de 1 h e chama o sync |
| Reserva venceu e o estoque acabou antes do pagamento | o `inventory` tenta baixar mesmo vencida; sem disponível, `stock.commit.failed` cancela o pedido e o estorno sai sozinho (`docs/event-contracts.md` §10.5) |
| A loja demora a despachar | é operação da própria loja, não regra do sistema: `GET /shipments/manage?status=pending` é a fila |
| Comprador nunca confirma a entrega | pedido fica `shipped` para sempre e ninguém avalia — pendência em §13.2 |

---

## 13. Decisões registradas e pendências

### 13.1 Decisões

| Data | Tema | Decisão | Onde aparece |
|---|---|---|---|
| 2026-09-18 | Modelo de negócio | **loja única, não marketplace** — um só `owner` | sem cadastro de vendedor nem `idOwner` nos contratos; checkout fecha o carrinho inteiro; origem do frete em configuração |
| 2026-09-18 | Conta da loja | o `owner` nasce pela config `app.store.owner-email` | **IMPLEMENTADO** — `ProvisionStoreOwner` (primeiro login e boot), `roles` em `UserResponse`, índice `owner_single_uk` (`V2__single_store_owner.sql`) |
| 2026-09-18 | Colunas de marketplace | removidas do schema | **IMPLEMENTADO** — `product.id_owner`/`owner_name` (`V3` no inventory), `order_item.id_owner` (`V3` no order), `shipment.id_address_owner` (`V2` no shipment) |
| 2026-09-18 | Origem do frete | endereço da loja em config, com latitude/longitude | só o destino é geocodificado (§10.1) |
| 2026-09-18 | Administração | sem papel `admin` | categorias só por migration (§7); gestão com o `owner` |
| 2026-09-18 | Sessão | token Bearer no header, sem cookie | refresh deslizante com o próprio token, teto de 7 dias (§5.2) |
| 2026-09-18 | Frete | R$ 1,00 por km, sem transportadora | §10.1; entrega confirmada pelo comprador (§10.2) |
| 2026-09-18 | Reputação | a avaliação é do produto | sem rating nem vitrine de vendedor |
| 2026-09-18 | Endereço no pedido | `orders.id_address` | **IMPLEMENTADO** — `V2__order_address.sql`, `Order`, `OrderEntity`, `OrderMapper` |
| 2026-09-18 | Redis | um Redis no compose, só cache | **IMPLEMENTADO** — serviço `redis`, sem porta publicada, `allkeys-lru` |
| 2026-09-18 | Validação de token | `SecurityConfig` em todos os serviços | **IMPLEMENTADO** — `inventory`, `order`, `payment`, `shipment` |

### 13.2 Pendências

1. **Endereço mutável entre checkout e pagamento** — o pedido guarda só `id_address`, e o
   `shipment` copia o endereço do `user` apenas no `order.confirmed`. Se o cliente editar o
   endereço nesse intervalo, o envio vai para o endereço novo com o frete calculado para o
   antigo. Saídas: endereço imutável (editar = criar um novo e remover o antigo) ou snapshot do
   endereço no pedido já no checkout. Recomendação: endereço imutável — não exige coluna nova.
2. **Seed de categorias** — `V3__seed_categories.sql` no `inventory`.
3. **`auth_time` no token do usuário** — `JwtTokenIssuer.issueForUser` precisa gravar essa claim
   para o refresh deslizante limitar a sessão a 7 dias.
4. **Token no redirect de login** — trocar `?token=` por `#token=` no `AuthSuccessHandler` (§5.1).
5. **Cobertura de coordenadas da BrasilAPI** — nem todo CEP tem latitude/longitude. Hoje isso é
   `422 ZIPCODE_NOT_GEOCODED` e bloqueia o checkout daquele endereço. Fallback possível: centro
   do município pelo código IBGE. Vale medir com CEPs reais antes de decidir.
6. **Confirmação automática de entrega** — sem ela, comprador que não confirma deixa o pedido em
   `shipped` para sempre. Sugestão: job que marca `delivered` N dias após `in_transit`.
7. **Histórico de status do pedido** — tabela `order_status_history` para um
   `GET /orders/{id}/timeline`.
8. **Portas locais** — `inventory`, `order`, `payment` e `shipment` não definem `server.port`.
   Pela IDE, todos sobem em 8080 e colidem com o gateway. No compose não importa. Sugestão:
   8082–8085, como na §1.
9. **Redis nos serviços** — o compose já tem o Redis, mas nenhum serviço declara
   `spring-boot-starter-data-redis` nem `spring.data.redis.host`. Entra junto com a primeira
   rota que usar cache.
10. **Regras de escopo por rota** — os `SecurityConfig` novos exigem só token válido com
    `aud=internal`. Os `hasAuthority("SCOPE_…")` de cada rota entram junto com os controllers,
    como já é no `user`.

### 13.3 Ordem sugerida de implementação

| # | Entrega | Destrava |
|---|---|---|
| 1 | Gateway como proxy: troca de token + resolução de papéis | qualquer rota chegar ao cliente |
| 2 | `user`: endereços | checkout |
| 3 | `inventory`: seed de categorias, `GET`/`POST`/`PATCH` de produtos | vitrine |
| 4 | `order`: carrinho | checkout |
| 5 | `shipment`: configuração da loja + geocodificação + `GET /shipping/quote` | frete |
| 6 | `order`: `POST /orders` | saga completa (já há consumidor dos dois lados) |
| 7 | Redis nos serviços + cache do catálogo | escala da vitrine |
| 8 | `payment`: Mercado Pago + webhook | receita |
| 9 | `shipment`: criação por evento, despacho, confirmação | entrega |
| 10 | `inventory`: avaliações | pós-venda |

---

**Fim do documento.**
