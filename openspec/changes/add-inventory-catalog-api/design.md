# Design

## Context

Existe em `micro-services/inventory`: módulo `product` (modelos `Product`, `Category`,
`ProductPhoto`, `ReservationStatus`; entidades; repositórios e adapters; `ReserveStockService` +
`StockHoldTransaction`; `ReleaseReservationService`; consumidores `OrderCreatedConsumer` e
`OrderCancelledConsumer`; `StockEventKafkaPublisher`) e módulo `review` (só persistência).
Contratos: `docs/api-contracts.md` §7 (sem as rotas de avaliação, que são de
`add-inventory-saga-reviews`, e sem escrita de categoria), `docs/event-contracts.md` §4.1, §5.1–5.2,
`docs/outbox-debezium.md` §6.4.3.

## Goals / Non-Goals

**Goals:** catálogo completo e reserva de estoque sem janela de perda.

**Non-Goals:** avaliações e novos consumidores (próxima change); upload de arquivo; ETag/304.

## Decisions

### D1. Disponibilidade nunca vem do cache
`available = stock − Σ reservas held com expires_at > now()`, sempre calculado por requisição
(consulta agregada por lista de ids para a vitrine). O cache guarda o produto **sem**
`available`; o controller completa depois. Por quê: `docs/api-contracts.md` §4.2.

### D2. Busca com Specification
`GET /products` usa JPA `Specification` para `q` (lower(name) like / description), `categoryId`
(repetível), `categorySlug`, `minPrice`, `maxPrice`, `minRating`, `inStock` e `sort` em
`price|rating|createdAt|name`. Facetas: contagem por categoria e faixa de preço do resultado
filtrado. `minPrice > maxPrice`, `size > 100`, sort inválido → 400; `q` com menos de 2 → 422.

### D3. Cache com Spring Cache + Redis
`RedisCacheManager` com caches `catalog:product` (10 min) e `catalog:categories` (1 h),
serializador JSON do Spring Data Redis compatível com Jackson 3, e um `CacheErrorHandler` que só
loga WARN (Redis fora não derruba rota). `@CacheEvict` em toda escrita de produto/foto/estoque.

### D4. Reserva pela outbox
`StockEventOutboxPublisher implements StockEventPublisherPort` grava na outbox
(`aggregateType=stock`, key = orderId, tópicos/aliases de `docs/event-contracts.md` §5.1–5.2).
`StockHoldTransaction.hold` publica `stock.reserved` dentro da própria transação;
`ReserveStockService` grava `stock.rejected` numa transação nova (`StockRejectionTransaction`)
depois do rollback. `ALREADY_RESERVED` não publica nada. Remover `StockEventKafkaPublisher` e o
`producer…type.mapping`.

### D5. Rotas internas de reserva
`confirm`: `held` → `confirmed` e `stock -= quantity` (idempotente se já confirmada; 409 se
`released`). `release`: `held` → `released` (409 se `confirmed`). Ambas numa transação só.

### D6. Escrita de produto
Todo produto pertence à loja (sem `idOwner`). `PATCH /products/{id}/stock` aceita exatamente um de
`stock` (absoluto) ou `delta`; resultado negativo ou abaixo do reservado ativo → 409. `DELETE` de
produto com reserva `held` ativa → 409. Fotos: no máximo 10 (409), URL absoluta https (422).

### D7. Segurança
GET de `/categories/**`, `/products`, `/products/{id}`, `/products/{id}/availability`,
`/products/{id}/photos` → `catalog:read`; `GET /products/manage` e toda escrita → `catalog:write`;
`/internal/**` → `internal:hydrate`. `/products/manage` declarado antes de `/products/{id}`.

### D8. Seed de categorias
`V5__seed_categories.sql` com 8 categorias de loja genérica (Eletrônicos, Informática, Casa,
Cozinha, Esporte, Moda, Livros, Brinquedos) e slugs em kebab-case sem acento.

## Risks / Trade-offs

- [Soma de reservas por lista de ids a cada busca] → índice parcial
  `stock_reservation_active_idx` já cobre; aceitável para o volume de uma loja.
