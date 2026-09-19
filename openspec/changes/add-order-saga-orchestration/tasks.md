# Tasks

## 1. Base

- [ ] 1.1 Conferir/ajustar `OrderStateMachine` para a tabela completa de `docs/event-contracts.md` §3.1 com monotonicidade (design D3); verificar compile
- [ ] 1.2 Criar os records de entrada `StockCommittedEvent`, `StockCommitFailedEvent`, `PaymentApprovedEvent`, `PaymentFailedEvent`, `PaymentRefundedEvent`, `ShipmentStatusChangedEvent` e `UserDeletedEvent` (módulo `cart`) e atualizar o `type.mapping` de consumidor em `application.properties` (design D4); verificar compile

## 2. Estoque

- [ ] 2.1 Estender `StockEventsConsumer`: `stock.reserved` (projeção), `stock.committed` e `stock.commit.failed` com os casos de uso transacionais (design D1/D2); verificar compile

## 3. Pagamento

- [ ] 3.1 Criar `PaymentEventsConsumer` e os casos de uso de `payment.approved`, `payment.failed` e `payment.refunded` (design D2); verificar compile

## 4. Envio e conta

- [ ] 4.1 Criar `ShipmentEventsConsumer` e o caso de uso de `shipment.status.changed` (design D2); verificar compile
- [ ] 4.2 Criar `UserDeletedConsumer` no módulo `cart` com o soft delete do carrinho; verificar compile

## 5. Verificação

- [ ] 5.1 Rodar `mvn -q -DskipTests compile` em `micro-services/order` sem erro
