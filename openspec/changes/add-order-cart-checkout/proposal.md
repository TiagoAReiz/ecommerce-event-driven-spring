# Proposal

## Why

O `order` só sabe cancelar pedido por evento de estoque. Não há carrinho, não há checkout, o
cliente não consulta os próprios pedidos e a loja não vê as vendas. E, como o `inventory`, ele
publica com `KafkaTemplate` depois do commit.

## What Changes

- Carrinho: `GET /cart` hidratado pelo `inventory` com `issues[]`, `POST /cart/items`,
  `PUT /cart/items/{idProduct}`, `DELETE /cart/items/{idProduct}`, `DELETE /cart`.
- Checkout `POST /orders` com `Idempotency-Key`: revalida preço/estoque no `inventory`, confere o
  endereço no `user`, calcula o frete no `shipment`, grava pedido `pending` com snapshots e
  `id_address`, esvazia o carrinho e grava `order.created` na outbox.
- Consultas: `GET /orders`, `GET /orders/{id}` (com projeções de pagamento/envio),
  `GET /orders/manage`, `GET /internal/orders/{id}`.
- Cancelamento `POST /orders/{id}/cancel` pela máquina de estados de
  `docs/event-contracts.md` §3.1, com `order.cancelled` e, havendo pagamento,
  `order.refund.requested`; `PATCH /internal/orders/{id}/status` de contingência.
- Job de TTL do checkout: cancela `pending` com mais de 30 min.
- Migration de projeções no pedido (pagamento, envio, reserva, motivo de cancelamento).
- Todos os eventos que o `order` publica passam pela outbox; `OrderEventKafkaPublisher` sai.
- Kit de plataforma completo.

## Capabilities

### New Capabilities
- `shopping-cart`: carrinho do cliente com hidratação e sinalização de problemas.
- `checkout`: criação idempotente de pedido com revalidação de preço, endereço e frete.
- `order-management`: consulta de pedidos pelo cliente e pela loja, cancelamento e expiração de pedido não pago.

### Modified Capabilities

## Impact

- `micro-services/order/**` apenas. Migrations novas `V4__outbox.sql`, `V5__order_projections.sql`.
- Depende das rotas internas de `user` (`/internal/addresses/{id}`), `inventory`
  (`/internal/products`) e `shipment` (`/shipping/quote`), definidas no config.
- A change `add-order-saga-orchestration` usa a port de publicação e as projeções criadas aqui.
