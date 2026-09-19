# Tasks

## 1. Base

- [x] 1.1 Adicionar `spring-boot-starter-data-redis` e `spring-boot-starter-validation` ao `micro-services/api-gateway/pom.xml`; verificar com `mvn -q -DskipTests compile`
- [x] 1.2 Em `src/main/resources/application.properties`: `spring.data.redis.host`, `app.front-url`, `app.services.{user,inventory,order,payment,shipment}.url`, `app.service-clients.{user,inventory,order,payment,shipment}`, `app.mercadopago.webhook-secret` com os nomes de env do config; verificar compile
- [x] 1.3 Criar `shared/web/{ApiException,NotFoundException,ConflictException,BadRequestException,UnprocessableException,ForbiddenException,GlobalExceptionHandler}` conforme o kit de plataforma (ProblemDetail com code, requestId, timestamp); verificar compile

## 2. Tokens

- [x] 2.1 Estender `TokenIssuerPort`/`JwtTokenIssuer` com `issueForUser(UserResponse, List<String> roles, Instant authTime)` (claims `roles`, `auth_time`, `jti`), `issueInternal(sub, roles, scopes)` e `issueForService(clientId)` (design D3); adicionar `roles` a `UserResponse`; verificar compile
- [x] 2.2 Criar `modules/auth/application/ScopePolicy` com os conjuntos de escopo por papel do config; verificar compile
- [x] 2.3 Criar `JwtDecoder` de `aud=front` a partir da chave pública do `RSAKey`, com validadores de issuer, audiência e denylist Redis (`auth:denylist:{jti}`) (design D1); verificar compile

## 3. Segurança e filtros

- [x] 3.1 Reescrever `config/SecurityConfig` com as duas cadeias do design D1 e a lista de rotas públicas com método; verificar compile
- [x] 3.2 Criar `config/RequestIdFilter`, `config/RateLimitFilter` e a configuração de CORS (design D8); verificar compile

## 4. Sessão e login

- [x] 4.1 `AuthSuccessHandler`: redirect com `#token=`, token com `roles` e `auth_time` (design D5); verificar compile
- [x] 4.2 Criar `modules/auth/infra/outbound/cache/ProfileCache` (Redis + fallback) e o método `findProfile(id)` em `UserMicroservicePort`/`UserMicroservice` chamando `GET /internal/users/{id}/profile` com `issueForService("api-gateway")`; verificar compile
- [x] 4.3 Criar `modules/auth/infra/inbound/controllers/SessionController` com `GET /api/v1/auth/session`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` (design D5, códigos de `docs/api-contracts.md` §5); verificar compile
- [x] 4.4 Criar `ServiceTokenController` `POST /auth/service-token` (design D6); verificar compile

## 5. Proxy

- [x] 5.1 Criar `modules/proxy/RouteTable` e `modules/proxy/ProxyController` (design D2), montando o token interno: usuário autenticado → `issueInternal(sub, roles do ProfileCache, ScopePolicy)`; sem autenticação → `issueInternal("anonymous", [], escopos de anônimo)`; verificar compile
- [x] 5.2 Tratar conexão recusada (503) e timeout (504) com ProblemDetail e reescrever `Location`; verificar compile

## 6. Webhook

- [x] 6.1 Criar `modules/webhook/MercadoPagoSignature` (manifest + HMAC-SHA256 + janela de 5 min) e `MercadoPagoWebhookController` `POST /public/webhooks/mercadopago` com o repasse e o mapeamento de status do design D7; verificar compile

## 7. Verificação

- [x] 7.1 Rodar `mvn -q -DskipTests compile` em `micro-services/api-gateway` sem erro e conferir que nenhum arquivo contém a palavra proibida do config
