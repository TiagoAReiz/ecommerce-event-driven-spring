# Proposal

## Why

O `inventory` só reserva estoque por evento. Não há vitrine, cadastro de produto, controle de
estoque pela loja nem as rotas internas que o checkout usa para revalidar preço. E ele ainda
publica com `KafkaTemplate` depois do commit, a janela de perda que o ADR-001 fecha.

## What Changes

- Categorias somente leitura, semeadas por migration (`GET /categories`, `GET /categories/{idOrSlug}`).
- Vitrine e busca: `GET /products` (filtros, ordenação, paginação, facetas), `GET /products/{id}`,
  `GET /products/{id}/availability`, `GET /products/{id}/photos`.
- Gestão da loja: `GET /products/manage`, `POST/PUT/PATCH/DELETE /products`, `PATCH /products/{id}/stock`,
  fotos por URL (`POST`, `PUT .../photos/order`, `DELETE`).
- Rotas internas: `GET /internal/products?ids=`, `GET /internal/reservations?orderId=`,
  `POST /internal/reservations/{orderId}/confirm|release`.
- Cache Redis de produto e categorias com invalidação na escrita.
- Outbox: `stock.reserved` e `stock.rejected` passam a ser gravados na transação da reserva
  (ADR-001 §6.4.3); `StockEventKafkaPublisher` sai.
- Kit de plataforma: ProblemDetail, escopos por rota, DLT, token de serviço, env do compose.

## Capabilities

### New Capabilities
- `catalog`: categorias, vitrine, busca e detalhe de produto.
- `catalog-management`: cadastro de produto, fotos e estoque pela loja.
- `stock-reservation`: disponibilidade, reserva por evento e rotas internas de reserva e hidratação de produto.

### Modified Capabilities

## Impact

- `micro-services/inventory/**` apenas. Migrations novas `V4__outbox.sql`, `V5__seed_categories.sql`.
- Nenhuma mudança no contrato de fio de `stock.reserved`/`stock.rejected`.
- Consumidor da rota interna de produtos: `order` (`add-order-cart-checkout`).
