# Proposal

## Why

O `order` é o orquestrador da saga (`docs/event-contracts.md` §1), mas hoje só reage a
`stock.rejected`. Sem os demais consumidores, um pagamento aprovado não vira `paid`, o estoque
não é baixado, o envio não nasce e o dinheiro não volta quando algo dá errado.

## What Changes

- Consumidores de `stock.reserved`, `stock.committed`, `stock.commit.failed`,
  `payment.approved`, `payment.failed`, `payment.refunded`, `shipment.status.changed` e
  `user.deleted`, cada um com a tabela de resultados de `docs/event-contracts.md`.
- Transições da máquina de estados §3.1 dirigidas por evento, publicando `order.paid`,
  `order.confirmed`, `order.delivered`, `order.cancelled` e `order.refund.requested` pela outbox.
- Casos especiais: pagamento depois do cancelamento, pagamento em dobro, valor divergente (DLT),
  estorno total antes do envio, eco de cancelamento, evento atrasado que faria o pedido voltar.

## Capabilities

### New Capabilities
- `order-saga`: orquestração do pedido por eventos de estoque, pagamento, envio e remoção de conta.

### Modified Capabilities

## Impact

- `micro-services/order/**` apenas. Depende de `add-order-cart-checkout` (port de publicação,
  projeções, `OrderStateMachine`, `KafkaConfig`).
