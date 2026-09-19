# Spec Delta

## Purpose

Consulta de pedidos por cliente e loja, cancelamento sob máquina de estados, e expiração automática de pedidos pendentes não pagos.

## ADDED Requirements

### Requirement: Listar pedidos do usuário
O sistema SHALL retornar lista paginada de pedidos do usuário autenticado, com filtros de status e data, ordenada por data de criação descendente por padrão.

#### Scenario: Listar pedidos com sucesso
- **WHEN** usuário com escopo `orders:read` executa `GET /orders`
- **THEN** sistema retorna status `200` com array `content[]` contendo pedidos com id, status, itemsCost, freightCost, totalCost, itemCount, firstItem (productName e productPhotoUrl), createdAt; e paginação com number, size, totalElements, totalPages

#### Scenario: Filtrar por status único
- **WHEN** usuário executa `GET /orders?status=paid`
- **THEN** sistema retorna apenas pedidos com status `paid`

#### Scenario: Filtrar por múltiplos status
- **WHEN** usuário executa `GET /orders?status=pending&status=paid`
- **THEN** sistema retorna pedidos com status `pending` ou `paid`

#### Scenario: Filtrar por intervalo de data
- **WHEN** usuário executa `GET /orders?from=2026-09-01&to=2026-09-30`
- **THEN** sistema retorna pedidos criados entre 01/09 e 30/09

#### Scenario: Paginação padrão
- **WHEN** usuário executa `GET /orders` sem parâmetros de paginação
- **THEN** sistema retorna página 0 com tamanho 20 (default)

#### Scenario: Paginação customizada
- **WHEN** usuário executa `GET /orders?page=2&size=50`
- **THEN** sistema retorna página 2 com 50 elementos por página

#### Scenario: Ordenação padrão
- **WHEN** usuário executa `GET /orders`
- **THEN** sistema retorna pedidos ordenados por `createdAt` descendente (mais recentes primeiro)

#### Scenario: Status inválido
- **WHEN** `GET /orders?status=inexistente`
- **THEN** sistema retorna status `400` com erro `INVALID_STATUS`

#### Scenario: Data divergente (from > to)
- **WHEN** `GET /orders?from=2026-09-30&to=2026-09-01`
- **THEN** sistema retorna status `400` com erro `INVALID_DATE_RANGE`

#### Scenario: Paginação inválida
- **WHEN** `GET /orders?page=abc` ou `GET /orders?size=-1`
- **THEN** sistema retorna status `400`

#### Scenario: Sem escopo de leitura
- **WHEN** token não contém escopo `orders:read`
- **THEN** sistema retorna status `403`

#### Scenario: Sem autenticação
- **WHEN** `GET /orders` sem token
- **THEN** sistema retorna status `401`

---

### Requirement: Visualizar detalhe do pedido do usuário
O sistema SHALL retornar detalhes completos de um pedido específico do usuário autenticado, incluindo itens com snapshots, custos, e projeções de pagamento e envio. Retorna `404` para pedido de outro cliente.

#### Scenario: Detalhe de pedido bem-sucedido
- **WHEN** usuário com escopo `orders:read` executa `GET /orders/3301` e é dono do pedido
- **THEN** sistema retorna status `200` com id, status, idAddress, items[] (id, idProduct, productName, productPhotoUrl, priceAtTime, quantity, lineTotal), itemsCost, freightCost, totalCost, payment (id, status, provider) ou null, shipment (id, status, trackingCode) ou null, createdAt, updatedAt

#### Scenario: Projeção de pagamento presente
- **WHEN** pedido recebeu evento `payment.approved`
- **THEN** campo `payment` contém id, status, provider (não é null)

#### Scenario: Projeção de envio presente
- **WHEN** pedido recebeu evento `order.confirmed`
- **THEN** campo `shipment` contém id, status, trackingCode (pode ser null se ainda não informado)

#### Scenario: Pedido não existe
- **WHEN** `GET /orders/9999999` para pedido inexistente
- **THEN** sistema retorna status `404`

#### Scenario: Pedido de outro cliente
- **WHEN** usuário A tenta `GET /orders/{idDoPedidoDoUsuarioB}`
- **THEN** sistema retorna status `404` (não diferencia entre "não existe" e "não é seu")

#### Scenario: ID não-numérico
- **WHEN** `GET /orders/abc`
- **THEN** sistema retorna status `400`

#### Scenario: Sem escopo
- **WHEN** token não contém escopo `orders:read`
- **THEN** sistema retorna status `403`

---

### Requirement: Listar pedidos da loja (gestão)
O sistema SHALL retornar lista de **todos** os pedidos da loja (qualquer cliente) para usuário com papel `owner` e escopo `sales:read`, com filtros e paginação.

#### Scenario: Listar todos os pedidos
- **WHEN** usuário com papel `owner` e escopo `sales:read` executa `GET /orders/manage`
- **THEN** sistema retorna status `200` com array `content[]` similar a `/orders`, mas cada linha contém `idCustomer`

#### Scenario: Filtrar por cliente
- **WHEN** `GET /orders/manage?customerId=42`
- **THEN** sistema retorna apenas pedidos do cliente 42

#### Scenario: Filtrar por status
- **WHEN** `GET /orders/manage?status=pending`
- **THEN** sistema retorna apenas pedidos com status `pending`

#### Scenario: Sem papel owner
- **WHEN** usuário não é `owner` mas tem escopo `sales:read`
- **THEN** sistema retorna status `403`

#### Scenario: Sem escopo sales:read
- **WHEN** usuário é `owner` mas token não contém `sales:read`
- **THEN** sistema retorna status `403`

#### Scenario: Rota acessada antes de /orders/{id}
- **WHEN** cliente tenta `GET /orders/manage`
- **THEN** sistema retorna `403` ou `404` (rota protegida por papel, não por autenticação simples)

---

### Requirement: Leitura interna de pedido
O sistema SHALL retornar pedido completo para consumo servidor-a-servidor (payment, shipment), sem ocultar campos, com autenticação de token de serviço com escopo `internal:hydrate`.

#### Scenario: Leitura interna bem-sucedida
- **WHEN** serviço downstream com escopo `internal:hydrate` executa `GET /internal/orders/3301`
- **THEN** sistema retorna status `200` com pedido completo: id, idCustomer, status, idAddress, items[], itemsCost, freightCost, totalCost, createdAt, updatedAt (sem projeção de payment/shipment; payment valida valor, shipment monta o envio)

#### Scenario: Pedido não existe
- **WHEN** `GET /internal/orders/9999999`
- **THEN** sistema retorna status `404`

#### Scenario: Sem escopo internal:hydrate
- **WHEN** token não contém escopo `internal:hydrate`
- **THEN** sistema retorna status `403`

---

### Requirement: Cancelar pedido com máquina de estados
O sistema SHALL cancelar pedido respeitando a máquina de estados: `pending` → `cancelled` sem estorno, `paid` ou `processing` → `cancelled` com `order.refund.requested`, `processing` apenas pelo owner. Publica `order.cancelled` e, se houver pagamento, `order.refund.requested`.

#### Scenario: Cancelar pedido pending pelo cliente
- **WHEN** usuário (cliente do pedido) com escopo `orders:write` executa `POST /orders/3301/cancel` com `{reason: "Comprei por engano"}` e pedido está em status `pending`
- **THEN** sistema transiciona para `cancelled`, publica `order.cancelled`, retorna `200` com pedido cancelado, e **não** publica `order.refund.requested` (nenhum pagamento capturado)

#### Scenario: Cancelar pedido paid pelo cliente
- **WHEN** usuário (cliente do pedido) com escopo `orders:write` executa `POST /orders/3301/cancel` e pedido está em status `paid`
- **THEN** sistema transiciona para `cancelled`, publica `order.cancelled` e `order.refund.requested` (com `payment_id`), retorna `200` com pedido cancelado

#### Scenario: Cancelar pedido processing pelo owner
- **WHEN** usuário com papel `owner`, escopo `sales:read` e `orders:write` executa `POST /orders/3301/cancel` e pedido está em status `processing`
- **THEN** sistema transiciona para `cancelled`, publica `order.cancelled` e `order.refund.requested`, retorna `200`

#### Scenario: Cancelar pedido processing pelo cliente
- **WHEN** usuário (cliente do pedido) tenta `POST /orders/3301/cancel` e pedido está em `processing`
- **THEN** sistema retorna status `403` (só owner pode cancelar `processing`)

#### Scenario: Cancelar pedido shipped
- **WHEN** `POST /orders/3301/cancel` e pedido está em `shipped`
- **THEN** sistema retorna status `409` com erro `INVALID_STATE_TRANSITION` (devolução, não cancelamento)

#### Scenario: Cancelar pedido já cancelado
- **WHEN** `POST /orders/3301/cancel` e pedido já está em `cancelled`
- **THEN** sistema retorna status `409` com erro `ORDER_ALREADY_CANCELLED`

#### Scenario: Cancelar pedido de outro cliente
- **WHEN** usuário A tenta `POST /orders/{idDoPedidoDoUsuarioB}/cancel`
- **THEN** sistema retorna status `404` (não diferencia entre "não existe" e "não é seu")

#### Scenario: Reason vazio
- **WHEN** `POST /orders/3301/cancel` com `{reason: ""}`
- **THEN** sistema aceita (reason pode ser vazio ou omitido)

#### Scenario: Reason muito longo
- **WHEN** `POST /orders/3301/cancel` com `reason` acima de 500 caracteres
- **THEN** sistema retorna status `400` com erro `REASON_TOO_LONG`

#### Scenario: Sem escopo orders:write
- **WHEN** token não contém escopo `orders:write`
- **THEN** sistema retorna status `403`

#### Scenario: Content-Type inválido
- **WHEN** `POST /orders/3301/cancel` com `Content-Type: text/plain`
- **THEN** sistema retorna status `415`

---

### Requirement: Expiração automática de pedidos pendentes
O sistema SHALL executar job agendado que cancela pedidos em status `pending` com mais de 30 minutos de criação, publicando `order.cancelled` com motivo "pagamento nao confirmado em 30 minutos".

#### Scenario: Job executa periodicamente
- **WHEN** job `PendingOrderExpiryJob` é agendado com `@Scheduled(fixedDelay = 60000)`
- **THEN** sistema executa a cada 60 segundos, varrendo pedidos `pending` com `created_at < now() - 30 min`

#### Scenario: Pedido expirado é cancelado
- **WHEN** pedido `pending` foi criado há 31 minutos
- **THEN** job cancela o pedido (transição para `cancelled`), publica `order.cancelled` com motivo "pagamento nao confirmado em 30 minutos", **não** publica `order.refund.requested` (nenhum pagamento capturado em `pending`)

#### Scenario: Pedido recente não é expirado
- **WHEN** pedido `pending` foi criado há 5 minutos
- **THEN** job ignora e deixa o pedido em `pending`

#### Scenario: Pedido não-pending não é tocado
- **WHEN** pedido está em `paid`, `processing`, `shipped`, `cancelled` ou `delivered`
- **THEN** job não modifica (transição é apenas `pending` → `cancelled`)

#### Scenario: Expiration publica evento
- **WHEN** pedido expira por TTL
- **THEN** `order.cancelled` é publicado na outbox (inventory libera estoque, shipment cancela envio)

---

### Requirement: Alteração manual de status (contingência)
O sistema SHALL permitir alteração de status via `PATCH /internal/orders/{id}/status` para reconciliação manual, validando transições, exigindo escopo `internal:hydrate` e justificativa obrigatória.

#### Scenario: Alterar status com transição válida
- **WHEN** serviço (token com `internal:hydrate`) executa `PATCH /internal/orders/3301/status` com `{status: "paid", reason: "reconciliação manual do pagamento 9901"}`
- **THEN** sistema valida transição, grava novo status, retorna `200` com pedido atualizado

#### Scenario: Transição inválida
- **WHEN** `PATCH /internal/orders/3301/status` tenta `cancelled` → `paid`
- **THEN** sistema retorna status `409` com erro `INVALID_STATE_TRANSITION` (só anda para frente)

#### Scenario: Status fora do enum
- **WHEN** `PATCH /internal/orders/3301/status` com `{status: "inexistente"}`
- **THEN** sistema retorna status `400` com erro `INVALID_STATUS`

#### Scenario: Reason ausente
- **WHEN** `PATCH /internal/orders/3301/status` sem `reason`
- **THEN** sistema retorna status `422` com erro `REASON_REQUIRED` (mudança manual exige justificativa)

#### Scenario: Sem escopo internal:hydrate
- **WHEN** token não contém `internal:hydrate`
- **THEN** sistema retorna status `403`

#### Scenario: Pedido não existe
- **WHEN** `PATCH /internal/orders/9999999/status`
- **THEN** sistema retorna status `404`
