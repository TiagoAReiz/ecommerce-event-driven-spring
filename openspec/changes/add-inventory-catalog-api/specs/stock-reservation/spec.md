# Spec Delta

## Purpose

Gerencia reservas de estoque acionadas por pedidos, calcula disponibilidade em tempo real e fornece rotas internas para consulta e manipulação de reservas.

## ADDED Requirements

### Requirement: Reservar estoque por evento order.created

O sistema SHALL consumir evento `order.created` e reservar estoque para todos os itens do pedido por 30 minutos. Resultado de sucesso ou fracasso (stock.reserved ou stock.rejected) DEVE ser gravado atomicamente na transacao de reserva via outbox.

#### Scenario: Reserva com sucesso
- **WHEN** evento `ecommerce.order.created.v1` chega com pedido de 2 unidades do produto 118 e disponivel >= 2
- **THEN** sistema cria linha de reserva com status `held`, `expires_at = now + 30min`, grava `stock.reserved` na outbox na mesma transacao, HTTP 202

#### Scenario: Estoque insuficiente
- **WHEN** evento `ecommerce.order.created.v1` chega com pedido de 10 unidades do produto 118 e disponivel = 5
- **THEN** sistema faz rollback, grava `stock.rejected` na outbox com `productId` do primeiro produto sem disponivel, HTTP 202

#### Scenario: Produto nao existe
- **WHEN** evento `ecommerce.order.created.v1` chega com pedido para productId que nao existe
- **THEN** sistema faz rollback, grava `stock.rejected` com esse productId, HTTP 202

#### Scenario: Reentrega (reserva ja existe)
- **WHEN** evento `ecommerce.order.created.v1` chega para orderId que ja tem reserva gravada
- **THEN** sistema ignora, nao altera nada, nao publica evento, HTTP 202

#### Scenario: Itens processados em ordem de ID para evitar deadlock
- **WHEN** evento `ecommerce.order.created.v1` chega com multiplos itens
- **THEN** sistema processa os itens ordenado por productId para evitar deadlock em atualizacoes concorrentes

### Requirement: Calcular disponibilidade

O sistema SHALL calcular disponibilidade como estoque bruto menos a soma de reservas held cujo `expires_at > now()`. Reservas vencidas nao entram na soma.

#### Scenario: Disponibilidade simples
- **WHEN** produto tem stock=15 e nenhuma reserva held
- **THEN** disponivel = 15

#### Scenario: Disponibilidade com reservas ativas
- **WHEN** produto tem stock=15 e 3 unidades em reservas held com `expires_at > now()`
- **THEN** disponivel = 12

#### Scenario: Reservas vencidas nao contam
- **WHEN** produto tem stock=15, 5 unidades em held vencidas e 2 em held ativas
- **THEN** disponivel = 13, as vencidas nao entram na soma

### Requirement: Confirmar reserva

O sistema SHALL permitir que a rota interna `POST /internal/reservations/{orderId}/confirm` transicione reservas de `held` para `confirmed` e deduza do estoque bruto. Operacao DEVE ser idempotente e atomica.

#### Scenario: Confirmar reserva com sucesso
- **WHEN** sistema interno faz `POST /internal/reservations/3301/confirm` para reserva no status `held`
- **THEN** reserva vai para status `confirmed`, stock do produto eh decrementado pelo quantity da reserva, HTTP 200

#### Scenario: Idempotencia: confirmacao duplicada
- **WHEN** sistema faz `POST /internal/reservations/3301/confirm` duas vezes
- **THEN** primeira confirma a reserva, segunda confirma novamente sem erro, HTTP 200, sem decrementar stock duas vezes

#### Scenario: Confirmacao de reserva already released
- **WHEN** sistema faz `POST /internal/reservations/3301/confirm` para uma reserva no status `released`
- **THEN** sistema retorna HTTP 409 com ProblemDetail

#### Scenario: Reserva nao encontrada
- **WHEN** sistema faz `POST /internal/reservations/9999/confirm`
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Liberar reserva

O sistema SHALL permitir que a rota interna `POST /internal/reservations/{orderId}/release` transicione reservas de `held` para `released`, abrindo estoque novamente. Operacao DEVE ser atomica.

#### Scenario: Liberar reserva com sucesso
- **WHEN** sistema interno faz `POST /internal/reservations/3301/release` para reserva no status `held`
- **THEN** reserva vai para status `released`, disponibilidade do produto volta a contar com essas unidades, HTTP 200

#### Scenario: Liberacao de reserva already confirmed
- **WHEN** sistema faz `POST /internal/reservations/3301/release` para uma reserva no status `confirmed`
- **THEN** sistema retorna HTTP 409 com ProblemDetail

#### Scenario: Reserva nao encontrada
- **WHEN** sistema faz `POST /internal/reservations/9999/release`
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Consultar reservas por pedido

O sistema SHALL permitir que a rota interna `GET /internal/reservations?orderId=` retorne todas as reservas de um pedido com seus status e dados do produto.

#### Scenario: Consultar reservas
- **WHEN** sistema interno faz `GET /internal/reservations?orderId=3301`
- **THEN** sistema retorna array de reservas com orderId, productId, quantity, status (held/confirmed/released), expires_at e dados do produto (id, name, photoUrl), HTTP 200

#### Scenario: Sem reservas
- **WHEN** sistema faz `GET /internal/reservations?orderId=9999`
- **THEN** sistema retorna array vazio, HTTP 200

#### Scenario: Sem token de servico
- **WHEN** cliente externo faz `GET /internal/reservations?orderId=3301`
- **THEN** sistema retorna HTTP 403 com ProblemDetail

### Requirement: Consultar produtos por lista de IDs

O sistema SHALL permitir que a rota interna `GET /internal/products?ids=` retorne dados basicos de multiplos produtos em uma unica chamada (usar JPA in-clause com JOIN para disponibilidade agregada).

#### Scenario: Consultar multiplos produtos
- **WHEN** sistema interno faz `GET /internal/products?ids=118,119,120`
- **THEN** sistema retorna { products: [{id, name, price, photoUrl, available, active}], missing: [] }, HTTP 200

#### Scenario: Alguns produtos nao encontrados
- **WHEN** sistema faz `GET /internal/products?ids=118,9999,119`
- **THEN** sistema retorna { products: [{id:118, ...}, {id:119, ...}], missing: [9999] }, HTTP 200

#### Scenario: Produto removido
- **WHEN** sistema faz `GET /internal/products?ids=118` onde 118 tem `deleted_at` preenchido
- **THEN** produto deletado nao aparece em `products`, aparece em `missing`, HTTP 200

#### Scenario: Disponibilidade agregada
- **WHEN** sistema faz `GET /internal/products?ids=118,119` e produto 118 tem stock=15 com 3 held e produto 119 tem stock=10 com 0 held
- **THEN** resposta inclui { id: 118, available: 12, ... } e { id: 119, available: 10, ... }, HTTP 200

#### Scenario: IDs vazios
- **WHEN** sistema faz `GET /internal/products?ids=`
- **THEN** sistema retorna HTTP 400 com ProblemDetail

#### Scenario: Sem token de servico
- **WHEN** cliente externo faz `GET /internal/products?ids=118`
- **THEN** sistema retorna HTTP 403 com ProblemDetail

### Requirement: Publicar eventos de reserva na outbox

O sistema SHALL gravar `stock.reserved` e `stock.rejected` na tabela `outbox` atomicamente com a mudanca de estado das reservas, seguindo o ADR-001 e sem usar `KafkaTemplate` direto.

#### Scenario: stock.reserved gravado na transacao
- **WHEN** reserva criada com status `held` e estoque suficiente
- **THEN** evento `ecommerce.stock.reserved.v1` com `orderId` como key eh gravado na outbox na mesma transacao, preparado para Debezium

#### Scenario: stock.rejected gravado apos rollback
- **WHEN** reserva falha por estoque insuficiente e transacao eh feita rollback
- **THEN** em transacao nova (SEM propagacao obrigatoria), evento `ecommerce.stock.rejected.v1` com `orderId` como key e `productId` do primeiro item sem estoque eh gravado na outbox

#### Scenario: Reentrega nao publica evento
- **WHEN** consumidor recebe evento order.created para orderId que ja tem reserva processada
- **THEN** nenhum evento eh publicado na outbox

### Requirement: Nunca cachear disponibilidade

O sistema SHALL sempre calcular disponibilidade por requisicao, nunca usando cache. Produto pode ser cacheado sem o campo `available`.

#### Scenario: Disponibilidade recalculada
- **WHEN** usuario faz `GET /products/118/availability` duas vezes em rapida sucessao
- **THEN** ambas requisicoes consultam o banco para calcular a soma de reservas, sem cache

#### Scenario: Produto em cache, disponibilidade nao
- **WHEN** `GET /products/118` retorna do cache mas `GET /products/118/availability` consulta banco
- **THEN** controller completa o campo `available` apos o retorno do cache

