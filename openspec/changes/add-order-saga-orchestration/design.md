# Design

## Context

Após `add-order-cart-checkout`: `OrderStateMachine`, `updateStatus` condicional, projeções em
`orders` (`payment_id`, `payment_status`, `shipment_id`, `shipment_status`, `tracking_code`,
`stock_reservation`, `cancel_reason`, `refunded_amount`), `OrderEventPublisherPort` completo com
adaptador de outbox, `KafkaConfig` com DLT e `StockEventsConsumer` (reservado/rejeitado).
Contratos: `docs/event-contracts.md` §3.1, §5, §6, §7.1, §8.1, §9.2, §10.

## Goals / Non-Goals

**Goals:** todas as linhas "Consumo no `order`" de `docs/event-contracts.md`.

**Non-Goals:** novos eventos ou rotas HTTP.

## Decisions

### D1. Um caso de uso transacional por evento
Cada consumidor chama um caso de uso `@Transactional` que (1) lê o pedido, (2) decide pelo
estado atual (nunca pela ordem de chegada), (3) aplica `updateStatus` condicional e/ou a projeção
e (4) publica os eventos seguintes pela port, tudo na mesma transação. Pedido inexistente →
`InvalidEventException` (DLT).

### D2. Tabela de reações

| Evento | Estado do pedido | Ação |
|---|---|---|
| `stock.reserved` | `pending` | `stock_reservation = reserved` |
| `stock.committed` | `paid` | → `processing`; publica `order.confirmed` |
| `stock.commit.failed` | `paid` | → `cancelled` (motivo com o produto); publica `order.cancelled` + `order.refund.requested(payment_id)` |
| `payment.approved` | `pending` e `amount == total_cost` | → `paid`, grava `payment_id`/`payment_status=captured`; publica `order.paid` |
| `payment.approved` | `cancelled` | publica `order.refund.requested(paymentId do evento)` |
| `payment.approved` | já pago por outro `paymentId` | publica `order.refund.requested(paymentId do evento)` |
| `payment.approved` | já pago pelo mesmo `paymentId` | ignora |
| `payment.approved` | `amount != total_cost` | `InvalidEventException` → DLT |
| `payment.failed` | `pending` | projeção `payment_status` (não regride se já `captured`) |
| `payment.refunded` `full` | `cancelled`/`shipped`/`delivered` | → `refunded`, `refunded_amount = totalRefunded` |
| `payment.refunded` `full` | `paid`/`processing` | → `cancelled` (publica `order.cancelled`) → `refunded` |
| `payment.refunded` parcial | qualquer | só `refunded_amount` |
| `shipment.status.changed` | — | tabela de `docs/event-contracts.md` §7.1: projeções; `in_transit` → `shipped`; `delivered` → `delivered` + `order.delivered`; `cancelled` com pedido `processing` → `cancelled` + `order.cancelled` + `order.refund.requested`; eco com pedido já `cancelled` → ignora |
| `user.deleted` | — | soft delete de `cart`/`cart_items` do usuário |

`order.delivered` leva `items[].productId` distintos e `deliveredAt = changedAt` do evento.

### D3. Monotonicidade
`OrderStateMachine` precisa aceitar `processing → delivered` e recusar qualquer transição para
trás; transição recusada por estado à frente = `ignorado` (log DEBUG), não erro.

### D4. Records e mapeamento
Records de entrada em `infra/inbound/messaging/events` exatamente com os campos de
`docs/event-contracts.md`; `type.mapping` de consumidor igual à §9.2. Consumidores:
`StockEventsConsumer` (estendido), `PaymentEventsConsumer`, `ShipmentEventsConsumer`,
`UserDeletedConsumer` (módulo `cart`). Payload inválido → `InvalidEventException`.

## Risks / Trade-offs

- [Dois eventos do mesmo pedido em tópicos diferentes chegando fora de ordem] → toda decisão
  pelo estado atual e `updateStatus` condicional; os caminhos convergem (`event-contracts` §10.6–10.7).
