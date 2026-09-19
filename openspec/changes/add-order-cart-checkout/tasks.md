# Tasks

## 1. Plataforma

- [x] 1.1 Adicionar `spring-boot-starter-validation` e `spring-boot-starter-data-redis` ao `micro-services/order/pom.xml`; trocar `application.properties` para os nomes de env do config com `server.port=8083`; verificar compile
- [x] 1.2 Criar `shared/web/*`, `shared/security/CurrentUser`, `shared/client/ServiceTokenProvider` e `RestClient`s para `inventory`, `user` e `shipment` (timeout 3 s); verificar compile
- [x] 1.3 Criar `V4__outbox.sql` (publicação `debezium_order_outbox`) e `shared/outbox/*` com `@EnableScheduling`; verificar compile
- [x] 1.4 Criar `config/KafkaConfig` (error handler + DLT, `InvalidEventException`, `NewTopic` para os seis tópicos `ecommerce.order.*.v1` do event-contracts e seus `-dlt`); verificar compile

## 2. Pedido: modelo e publicação

- [x] 2.1 Criar `V5__order_projections.sql` e atualizar `Order`, `OrderEntity`, `OrderMapper` (design D1); verificar compile
- [x] 2.2 Estender `OrderEventPublisherPort`, criar os records de evento que faltam e `OrderEventOutboxPublisher`; remover `OrderEventKafkaPublisher` e o `producer…type.mapping` (design D2); verificar compile
- [x] 2.3 Criar `OrderStateMachine` e `updateStatus` condicional no repositório; reescrever `OrderCancellationTransaction`/`CancelOrderService` para publicar dentro da transação (design D3), mantendo o comportamento atual do `StockEventsConsumer`; verificar compile

## 3. Carrinho

- [x] 3.1 Casos de uso e `CartController`: `GET /cart`, `POST /cart/items`, `PUT /cart/items/{idProduct}`, `DELETE /cart/items/{idProduct}`, `DELETE /cart` com hidratação e `issues` (design D6, códigos de `docs/api-contracts.md` §8); verificar compile

## 4. Checkout

- [x] 4.1 Criar `shared/idempotency/IdempotencyStore` (design D5); verificar compile
- [x] 4.2 Criar `CheckoutService` + `CheckoutTransaction` e `POST /orders` (design D4); verificar compile

## 5. Consultas e cancelamento

- [x] 5.1 `GET /orders` (filtros status, from/to, paginação), `GET /orders/{id}` (projeções), `GET /orders/manage`, `GET /internal/orders/{id}`; verificar compile
- [x] 5.2 `POST /orders/{id}/cancel` e `PATCH /internal/orders/{id}/status` (design D3); verificar compile
- [x] 5.3 Criar `PendingOrderExpiryJob` (design D7); verificar compile

## 6. Segurança

- [x] 6.1 Atualizar `config/SecurityConfig` com as regras do design D8; verificar compile

## 7. Verificação

- [x] 7.1 Rodar `mvn -q -DskipTests compile` em `micro-services/order` sem erro
