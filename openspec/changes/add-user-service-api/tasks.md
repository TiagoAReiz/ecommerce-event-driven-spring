# Tasks

## 1. Plataforma

- [x] 1.1 Adicionar `spring-boot-starter-validation` ao `micro-services/user/pom.xml` e trocar `application.properties` para os nomes de env do config (`DB_URL`, `JWKS_URI`, `KAFKA_BOOTSTRAP`) mantendo `server.port=8081` e `app.store.owner-email`; verificar compile
- [x] 1.2 Criar `shared/web/*` (exceções, `GlobalExceptionHandler`, `PageResponse`) e `shared/security/CurrentUser`; migrar o `UserController` para lançar as exceções do kit em vez de `ResponseStatusException`; verificar compile
- [x] 1.3 Criar `src/main/resources/db/migration/V3__outbox.sql` (outbox + publicação `debezium_user_outbox` + heartbeat, `docs/outbox-debezium.md` §6.2), `shared/outbox/{OutboxWriter,OutboxMessage,OutboxPurgeJob}` e `@EnableScheduling`; verificar compile
- [x] 1.4 Criar `config/KafkaConfig` com `NewTopic` de `ecommerce.user.deleted.v1` e `ecommerce.user.deleted.v1-dlt` e o bloco de producer Kafka em `application.properties`; verificar compile

## 2. Perfil

- [x] 2.1 Casos de uso `GetMyProfile`, `GetPublicProfile`, `UpdateMyProfile` (validações D4) e DTOs `MeResponse`, `PublicUserResponse`, `UpdateMeRequest`; rotas `GET /users/me`, `GET /users/{id}`, `PATCH /users/me` com os códigos de `docs/api-contracts.md` §6; verificar compile
- [x] 2.2 `DeleteMyAccount` (design D3): soft delete de usuário e endereços + `UserEventPublisherPort.publishUserDeleted` via `UserEventOutboxPublisher` na mesma transação; rota `DELETE /users/me` → 204; verificar compile

## 3. Endereços

- [x] 3.1 Completar `AddressRepositoryPort`/adapter com listagem paginada por usuário, busca ativa por id+usuário e `findByIdIncludingDeleted`; verificar compile
- [x] 3.2 Casos de uso e `AddressController`: `GET /users/me/addresses` (paginado), `GET /users/me/addresses/{id}`, `POST` (201 + Location), `PUT`/`PATCH` imutáveis (design D1), `DELETE` (204), com validações D4; verificar compile

## 4. Rotas internas

- [x] 4.1 `InternalUserController`: `GET /internal/users?ids=` (máx. 100, `missing[]`), `GET /internal/users/{id}/profile` (design D2), `GET /internal/addresses/{id}?userId=` (inclui removidos); verificar compile

## 5. Segurança

- [x] 5.1 Atualizar `config/SecurityConfig` com as regras do design D5; verificar compile

## 6. Verificação

- [x] 6.1 Rodar `mvn -q -DskipTests compile` em `micro-services/user` sem erro
