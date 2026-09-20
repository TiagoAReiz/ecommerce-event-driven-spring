# Design

## Context

Após `add-inventory-catalog-api`: kit de plataforma, outbox, `StockEventOutboxPublisher`,
`KafkaConfig` com DLT, cache e `RestClient` para o `user`. Módulo `review` tem modelos,
entidades e repositórios de `review` e `review_eligibility`. Contratos:
`docs/event-contracts.md` §4.2, §4.3, §4.5, §5.3, §5.4, §8.1 e `docs/api-contracts.md` §7
(rotas de avaliação).

## Goals / Non-Goals

**Goals:** metade de estoque da saga pós-pagamento e avaliações completas.

**Non-Goals:** moderação de texto (o 422 de moderação não é implementado).

## Decisions

### D1. Baixa de estoque (`CommitStockService` + `StockCommitTransaction`)
Numa transação, travando as linhas de `product` em ordem de id:
- reservas `held` do pedido (vencidas ou não): confirma se `stock − Σ outras held ativas ≥ qty`
  (a própria reserva não conta contra si), `stock -= qty`, status `confirmed`;
- sem reserva para o item: mesma checagem e cria a linha já `confirmed`;
- reserva `released`, ou falta disponível em qualquer item: rollback e, numa transação nova,
  libera as `held` do pedido e grava `stock.commit.failed`
  (`RESERVATION_RELEASED` | `EXPIRED_WITHOUT_STOCK`);
- tudo `confirmed` já (reentrega): não baixa de novo e **não** publica (a outbox já tem o
  `stock.committed` da primeira vez).
Sucesso grava `stock.committed` na mesma transação.

### D2. Devolução no cancelamento
`ReleaseReservationService` ganha o caminho `confirmed` → `released` com `stock += qty` numa
operação explícita e separada da liberação de `held` (o comentário atual do repositório explica
por que elas não se misturam).

### D3. Elegibilidade e LGPD
`order.delivered`: `INSERT … ON CONFLICT DO NOTHING` em `review_eligibility` por item.
`user.deleted`: `UPDATE review SET user_name = 'Usuário removido', user_photo_url = NULL WHERE id_user = ?`.

### D4. Avaliações
- `POST /products/{id}/reviews`: exige linha em `review_eligibility (sub, id, idOrder)` → senão
  403; duplicata → 409 (índice `review_user_product_order_uk`); snapshot de nome/foto via
  `user GET /internal/users?ids={sub}`; recalcula `product.rating` (média, 2 casas) e
  `rating_count` na mesma transação e evita o cache do produto.
- `PATCH /reviews/{id}`: só o autor, até 30 dias após a criação (409 depois); recalcula.
- `DELETE /reviews/{id}`: só o autor, soft delete; recalcula.
- `GET /products/{id}/reviews`: filtro `rate`, paginação, `summary` com distribuição 1–5.
- `GET /reviews/pending`: elegibilidades sem avaliação, com nome/foto do produto.
- `editedAt` na resposta = `updatedAt` quando diferente de `createdAt`, senão null.

### D5. Segurança
`GET /products/{id}/reviews`, `GET /reviews/{id}` → `catalog:read` ou `reviews:read`;
`GET /reviews/mine`, `GET /reviews/pending` → `reviews:read`; escrita → `reviews:write`.
`/reviews/mine` e `/reviews/pending` antes de `/reviews/{id}`.

### D6. Kafka
Records de entrada `OrderPaidEvent`, `OrderDeliveredEvent`, `UserDeletedEvent`; `type.mapping` de
consumidor igual a `docs/event-contracts.md` §9.1; payload inválido → `InvalidEventException`.

## Risks / Trade-offs

- [Recomprometer reserva vencida pode baixar estoque que outro pedido esperava] → a checagem de
  disponível conta as outras reservas ativas; se não couber, o pedido é cancelado e estornado.

## Decisoes de implementacao

- `CommitStockService` com transação própria para a baixa e uma transação separada para a falha:
  o rollback da primeira não pode levar junto o `stock.commit.failed`.
- Avaliações: `ReviewService` concentra as sete rotas; o snapshot do autor vem de
  `GET /internal/users?ids=` e, se o `user` estiver fora, a avaliação é gravada sem nome (log
  WARN) em vez de falhar — o texto da avaliação vale mais que o snapshot.
- Avaliação de outro autor responde 404, não 403, para não confirmar a existência do id.
- `rating` do produto é recalculado na mesma transação de criar, editar e remover avaliação.
- Segurança: `/reviews/mine` e `/reviews/pending` declarados antes de `/reviews/{id}`, e
  `POST /products/*/reviews` exige `reviews:write` (sem isso cairia na regra genérica de
  "qualquer token autenticado").
