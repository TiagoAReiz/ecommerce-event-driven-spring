# Proposal

## Why

Com o catálogo e a outbox no lugar (`add-inventory-catalog-api`), falta ao `inventory` a metade
da saga que acontece depois do pagamento: baixar o estoque, devolvê-lo no cancelamento,
conceder o direito de avaliar e respeitar a remoção de conta. E as avaliações, que dão o
`rating` do produto, ainda não existem.

## What Changes

- Consumidor de `order.paid`: baixa de estoque tudo-ou-nada, inclusive com reserva vencida ou
  inexistente se houver disponível; publica `stock.committed` ou `stock.commit.failed`.
- Consumidor de `order.cancelled` estendido: reserva `confirmed` volta para o estoque.
- Consumidor de `order.delivered`: grava `review_eligibility`.
- Consumidor de `user.deleted`: anonimiza as avaliações do usuário.
- Avaliações: `GET /products/{id}/reviews` (com distribuição), `GET /reviews/{id}`,
  `GET /reviews/mine`, `GET /reviews/pending`, `POST /products/{id}/reviews`,
  `PATCH /reviews/{id}`, `DELETE /reviews/{id}`, com recálculo do `rating` do produto.

## Capabilities

### New Capabilities
- `product-reviews`: avaliações de produto por quem comprou e recebeu, e o rating agregado.
- `stock-commitment`: baixa e devolução de estoque dirigidas pelos eventos de pagamento e cancelamento.

### Modified Capabilities

## Impact

- `micro-services/inventory/**` apenas. Depende de `add-inventory-catalog-api` já aplicado.
- Eventos consumidos: `order.paid`, `order.cancelled`, `order.delivered`, `user.deleted`.
  Produzidos: `stock.committed`, `stock.commit.failed`.
