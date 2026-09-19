# Design

## Context

Existe em `micro-services/order`: módulo `cart` (modelos, entidades, repositórios, adapters) e
módulo `order` (`Order` com `idAddress`, `OrderItem`, `OrderStatus`; `CancelOrderService` +
`OrderCancellationTransaction`; `StockEventsConsumer`; `OrderEventKafkaPublisher`; eventos
`OrderCreatedEvent`/`OrderCancelledEvent`). Contratos: `docs/api-contracts.md` §8,
`docs/event-contracts.md` §3.1 e §4, ADR-001 §6.4.

## Goals / Non-Goals

**Goals:** fluxo de compra até o pedido `pending` e todo o lado HTTP do pedido.

**Non-Goals:** consumidores de pagamento/envio/estoque novos (change `add-order-saga-orchestration`).

## Decisions

### D1. Projeções no pedido (`V5__order_projections.sql`)
```sql
ALTER TABLE orders
    ADD COLUMN payment_id        BIGINT,
    ADD COLUMN payment_status    VARCHAR(20),
    ADD COLUMN shipment_id       BIGINT,
    ADD COLUMN shipment_status   VARCHAR(30),
    ADD COLUMN tracking_code     VARCHAR(60),
    ADD COLUMN stock_reservation VARCHAR(20) NOT NULL DEFAULT 'pending',
    ADD COLUMN cancel_reason     TEXT,
    ADD COLUMN refunded_amount   NUMERIC(12,2) NOT NULL DEFAULT 0;
```
`Order`, `OrderEntity` e `OrderMapper` ganham os campos. `GET /orders/{id}` monta `payment`,
`shipment` e `stockReservation` a partir deles (null quando vazios).

### D2. Port de publicação completa, gravando na outbox
`OrderEventPublisherPort`: `publishOrderCreated(Order)`, `publishOrderCancelled(Long id, Long idCustomer,
String reason)`, `publishOrderPaid(Order, Long paymentId)`, `publishOrderConfirmed(Order)`,
`publishOrderDelivered(Order, Instant deliveredAt)`, `publishOrderRefundRequested(Long id, Long paymentId,
String reason)`. `OrderEventOutboxPublisher` grava cada um com o tópico, alias e payload de
`docs/event-contracts.md` §4 (records em `infra/outbound/messaging/events`). Toda chamada acontece
dentro da transação que muda o status. `OrderEventKafkaPublisher` e o `producer…type.mapping` saem.

### D3. Máquina de estados num lugar só
`OrderStateMachine` (domínio) com `canTransition(from, to)` pela tabela de
`docs/event-contracts.md` §3.1; o `OrderRepositoryPort.updateStatus(id, from, to)` condicional
(UPDATE … WHERE status = from) garante a corrida. Cancelamento:
- `pending` → `cancelled`: publica `order.cancelled`.
- `paid`/`processing` → `cancelled`: publica `order.cancelled` e `order.refund.requested`
  (com `payment_id`); `processing` só pelo `owner` (roles do token).
- demais → 409.
`OrderCancellationTransaction` passa a publicar dentro da transação (ADR-001 §6.4.3).

### D4. Checkout
`POST /orders` `{addressId, expectedTotalCost?}` segue a sequência de `docs/api-contracts.md` §8.2
(sem `idOwner`: loja única). Chamadas: `inventory GET /internal/products?ids=`,
`user GET /internal/addresses/{addressId}?userId={sub}` (404 → 404), `shipment GET /shipping/quote?zipcode=`.
As três chamadas acontecem **antes** de abrir a transação; a gravação do pedido, a limpeza do
carrinho e o `order.created` ficam numa transação só (`CheckoutTransaction`).
Falhas: `inventory` indisponível → 503 sem criar pedido; timeout → 504; 5xx → 502.

### D5. Idempotência (Redis)
Chave `idem:orders:{sub}:{Idempotency-Key}`. `SET NX` com marcador `IN_FLIGHT` (TTL 60 s); ao
terminar grava `{bodyHash, status, body}` (TTL 24 h). Repetição: mesmo hash → mesma resposta +
`Idempotency-Replayed: true`; hash diferente → 422 `IDEMPOTENCY_KEY_REUSED`; em voo → 409
`IDEMPOTENCY_IN_FLIGHT` com `Retry-After: 1`. Header ausente ou não UUID → 400. Redis fora →
segue sem idempotência e loga WARN.

### D6. Carrinho
Hidratação em lote pelo `inventory`; `issues` por item: `PRODUCT_UNAVAILABLE`, `INSUFFICIENT_STOCK`.
Timeout na hidratação → 200 com itens sem `product` e `issues:["HYDRATION_TIMEOUT"]`.
`POST /cart/items` soma quantidade (máx. 99 por item → 422; 50 linhas → 413).

### D7. TTL do checkout
`PendingOrderExpiryJob` (`@Scheduled(fixedDelay = 60000)`): para cada pedido `pending` com
`created_at < now() - 30 min`, cancela pela mesma transação de D3 com motivo
`"pagamento nao confirmado em 30 minutos"`.

### D8. Segurança
`/cart/**` → `cart:read` (GET) / `cart:write`; `GET /orders` e `GET /orders/{id}` → `orders:read`;
`POST /orders`, `POST /orders/{id}/cancel` → `orders:write`; `GET /orders/manage` → `sales:read`;
`/internal/**` → `internal:hydrate`. `/orders/manage` antes de `/orders/{id}`.

## Risks / Trade-offs

- [Chamadas síncronas no checkout aumentam a latência] → timeouts curtos (3 s) e revalidação
  obrigatória; sem ela não há garantia de preço.

## Decisoes de implementacao

- **Jackson 3 imports**: Usadas `tools.jackson.databind.json.JsonMapper` conforme Spring Boot 4.1, não `com.fasterxml.jackson.databind.ObjectMapper`.
- **OutboxWriter com JdbcClient**: Grava eventos na outbox dentro de transações MANDATORY usando cast(? as jsonb) para a coluna payload.
- **OrderStateMachine**: Criada como classe de domínio com método estático `canTransition()` que valida transições conforme tabela de event-contracts §3.1.
- **OrderCancellationTransaction**: Reescrita para publicar `order.cancelled` e `order.refund.requested` dentro da transação, removendo publicação post-commit.
- **CartService**: Implementada com hidratação via InventoryServiceClient e sinalização de `PRODUCT_UNAVAILABLE` e `INSUFFICIENT_STOCK` em `issues[]`.
- **CheckoutTransaction**: Grava pedido, limpa carrinho e publica `order.created` — tudo na mesma transacao conforme D4.
- **IdempotencyStore com Redis**: Utiliza StringRedisTemplate com NX atomicity e TTLs (60s IN_FLIGHT, 24h resultado). Se Redis falhar, loga WARN e permite prosseguir sem idempotência.
- **RestClients**: Implementados com timeout de 3s, interceptor de token de serviço e tratamento de ResourceAccessException/HttpServerErrorException.
- **SecurityConfig**: Regras por rota usando `hasAuthority("SCOPE_...")`, com rota `/orders/manage` posicionada ANTES de `/orders/{id}` para precedência.
- **Migrations V4 e V5**: V4 cria tabela outbox e publicação debezium_order_outbox; V5 adiciona projeções (payment, shipment, stock_reservation, etc.) à tabela orders.
- **CheckoutService (D4 real)**: Implementação completa que 1) valida carrinho não vazio, 2) revalida produtos com inventory (existência, atividade, estoque), 3) valida endereço no user service, 4) calcula frete no shipment, 5) valida expectedTotalCost se fornecido, 6) cria OrderItems com snapshots, 7) executa CheckoutTransaction. Erros retornam 409/422/404 conforme contrato.
- **OrderController**: Implementação real com 1) POST /orders com idempotency-key obrigatório (UUID), 2) GET /orders com filtros status/from/to e paginação (default createdAt,desc), 3) GET /orders/{id} com projeções payment/shipment, 4) POST /orders/{id}/cancel com validação de reason, 5) GET /orders/manage (posicionado ANTES de /{id}) com filtros e idCustomer na resposta. Usa ListMyOrdersUseCase, GetMyOrderUseCase, ListStoreOrdersUseCase.
- **InternalOrderController**: GET /internal/orders/{id} retorna InternalOrderResponse com id, idCustomer, status, idAddress, custos e items. PATCH /internal/orders/{id}/status com validacao de transicao permitida e reason obrigatório.
- **ListMyOrdersUseCase**: Filtra pedidos por usuario (idCustomer), status (repetível), intervalo de data, página e sort. Default sort: createdAt,desc. Retorna OrderSummary sem idCustomer.
- **GetMyOrderUseCase**: Retorna OrderDetailResponse com projeções de payment, shipment e cálculo de lineTotal por item. Valida propriedade (idCustomer) -> 404 se não pertence ao usuario.
- **ListStoreOrdersUseCase**: Lista todos os pedidos com filtros opcionais de status, customerId, intervalo de data. Retorna OrderSummary com idCustomer incluído para gestao da loja.
- **PendingOrderExpiryJob**: Job @Scheduled(fixedDelay=60000) que busca todos os pedidos pending com createdAt < now() - 30 min e chama OrderCancellationTransaction.cancel() para cada um com reason "pagamento nao confirmado em 30 minutos". Logs de erro não derrubam o job.
- **CancelOrderService/OrderCancellationTransaction**: Implementação já existente que valida transicoes (pending/paid->cancelled permitidos; processing->cancelled só para owner) e publica order.cancelled + order.refund.requested se havia paymentId dentro da mesma transacao.

### Correções da revisão

- A primeira implementação devolvia respostas fixas (lista vazia, detalhe `pending`, cancelamento
  que não cancelava) e criava pedido sem itens; foi refeita com casos de uso reais.
- Rotas internas no caminho do contrato (`/internal/orders/**`), não `/orders/internal/**`.
- Chamadas a `inventory`, `user` e `shipment` usam sempre o token de serviço (`internal:hydrate`);
  o token do usuário não tem esse escopo e seria recusado com 403.
- Falha de serviço chamado vira `UpstreamException`: 503 indisponível, 504 timeout, 502 resposta
  inválida (antes eram 400).
- `POST /orders` passou a usar o `IdempotencyStore`: replay com `Idempotency-Replayed: true`, 422
  para corpo diferente, 409 em voo; a marca em voo é liberada quando o checkout falha.
