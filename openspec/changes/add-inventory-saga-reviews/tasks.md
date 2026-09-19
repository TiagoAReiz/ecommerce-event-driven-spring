# Tasks

## 1. Baixa e devolução de estoque

- [ ] 1.1 Criar `OrderPaidEvent` (entrada), `OrderPaidConsumer` e `CommitStockService` + `StockCommitTransaction` com os métodos de repositório necessários (design D1); adicionar `publishStockCommitted`/`publishStockCommitFailed` a `StockEventPublisherPort` e ao adaptador de outbox; verificar compile
- [ ] 1.2 Estender `ReleaseReservationService`/`OrderCancelledConsumer` com o caminho `confirmed` → `released` e `stock += qty` (design D2); verificar compile

## 2. Elegibilidade e LGPD

- [ ] 2.1 Criar `OrderDeliveredEvent`, `OrderDeliveredConsumer` e `GrantReviewEligibilityService` (design D3); verificar compile
- [ ] 2.2 Criar `UserDeletedEvent`, `UserDeletedConsumer` e `AnonymizeUserReviewsService` (design D3); verificar compile
- [ ] 2.3 Atualizar o `type.mapping` de consumidor em `application.properties` (design D6); verificar compile

## 3. Avaliações

- [ ] 3.1 Casos de uso e `ReviewController`: `GET /products/{id}/reviews`, `GET /reviews/{id}`, `GET /reviews/mine`, `GET /reviews/pending` (design D4); verificar compile
- [ ] 3.2 `POST /products/{id}/reviews`, `PATCH /reviews/{id}`, `DELETE /reviews/{id}` com recálculo de rating e invalidação de cache (design D4); verificar compile

## 4. Segurança

- [ ] 4.1 Acrescentar as regras do design D5 ao `config/SecurityConfig`; verificar compile

## 5. Verificação

- [ ] 5.1 Rodar `mvn -q -DskipTests compile` em `micro-services/inventory` sem erro
