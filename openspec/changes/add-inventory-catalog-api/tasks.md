# Tasks

## 1. Plataforma

- [x] 1.1 Adicionar `spring-boot-starter-validation`, `spring-boot-starter-data-redis` e `spring-boot-starter-cache` ao `micro-services/inventory/pom.xml`; trocar `application.properties` para os nomes de env do config com `server.port=8082`; verificar compile
- [x] 1.2 Criar `shared/web/*`, `shared/security/CurrentUser`, `shared/client/ServiceTokenProvider` e um `RestClient` para o `user` (será usado pelas avaliações); verificar compile
- [x] 1.3 Criar `V4__outbox.sql` (publicação `debezium_inventory_outbox`) e `shared/outbox/*` com `@EnableScheduling`; verificar compile
- [x] 1.4 Criar `config/KafkaConfig` (error handler + DLT, `InvalidEventException`, `NewTopic` para `ecommerce.stock.{reserved,rejected,committed,commit.failed}.v1` e seus `-dlt`); verificar compile
- [x] 1.5 Criar `config/CacheConfig` (design D3); verificar compile

## 2. Outbox na reserva

- [x] 2.1 Criar `StockEventOutboxPublisher`, mover a publicação para dentro de `StockHoldTransaction` e criar `StockRejectionTransaction` (design D4); remover `StockEventKafkaPublisher` e o `producer…type.mapping`; validar `items` vazio/quantidade ≤ 0 no consumidor lançando `InvalidEventException`; verificar compile

## 3. Categorias

- [x] 3.1 Criar `V5__seed_categories.sql` (design D8) e `CategoryController` com `GET /categories` e `GET /categories/{idOrSlug}` (cacheados); verificar compile

## 4. Vitrine

- [x] 4.1 Criar a consulta de disponibilidade em lote no repositório de reservas e o `AvailabilityService` (design D1); verificar compile
- [x] 4.2 Criar a busca com `Specification` e facetas (design D2) e `GET /products`; verificar compile
- [x] 4.3 `GET /products/{id}` (com fotos e categoria), `GET /products/{id}/availability`, `GET /products/{id}/photos`, com os DTOs de `docs/api-contracts.md` §7.1 (dinheiro como string); verificar compile

## 5. Gestão

- [x] 5.1 `GET /products/manage` (filtro `status=active|out_of_stock|deleted|all`), `POST /products`, `PUT /products/{id}`, `PATCH /products/{id}`, `DELETE /products/{id}` (design D6); verificar compile
- [x] 5.2 `PATCH /products/{id}/stock` (design D6) e fotos: `POST /products/{id}/photos`, `PUT /products/{id}/photos/order`, `DELETE /products/{id}/photos/{photoId}`; verificar compile

## 6. Rotas internas

- [x] 6.1 `GET /internal/products?ids=` (máx. 100, `missing[]`, `active=false` para removido), `GET /internal/reservations?orderId=`, `POST /internal/reservations/{orderId}/confirm` e `/release` (design D5); verificar compile

## 7. Segurança

- [x] 7.1 Atualizar `config/SecurityConfig` com as regras do design D7; verificar compile

## 8. Verificação

- [x] 8.1 Rodar `mvn -q -DskipTests compile` em `micro-services/inventory` sem erro
