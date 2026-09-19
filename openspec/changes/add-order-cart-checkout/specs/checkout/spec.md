# Spec Delta

## Purpose

Criação idempotente de pedido com revalidação de preço, endereço e frete em transação atômica, bloqueando pagamento e estoque até confirmação.

## ADDED Requirements

### Requirement: Criar pedido com validações de revalidação
O sistema SHALL realizar checkout idempotente: revalida preço e estoque no `inventory`, confirma endereço no `user`, calcula frete no `shipment`, grava pedido com snapshots de produto, esvazia carrinho e publica `order.created` — tudo em uma transação. Sem `idOwner`: fechamento do carrinho inteiro como pedido único.

#### Scenario: Checkout bem-sucedido com sucesso
- **WHEN** usuário autenticado com escopo `orders:write` e Idempotency-Key UUID válido executa `POST /orders` com `{addressId: 15, expectedTotalCost: "724.80"}`
- **THEN** sistema valida carrinho não-vazio, revalida preços/estoque no `inventory`, confirma endereço no `user`, calcula frete no `shipment`, grava `orders` e `order_item` com status `pending`, snapshots de produto, `id_address`, esvazia carrinho, publica `order.created` e retorna `201` com pedido completo, `stockReservation: "pending"` e `nextStep: {action: "CREATE_PAYMENT", href: "/api/v1/payments"}`

#### Scenario: Carrinho vazio
- **WHEN** usuário tenta `POST /orders` com carrinho vazio
- **THEN** sistema retorna status `422` com erro `EMPTY_CART`

#### Scenario: Idempotência com replay bem-sucedido
- **WHEN** primeira requisição `POST /orders` com Idempotency-Key K1 retorna `201`; segunda requisição com mesma key K1 e mesmo corpo é executada
- **THEN** sistema retorna `201` com mesma resposta anterior, header `Idempotency-Replayed: true`, sem processar novamente

#### Scenario: Idempotência com corpo diferente
- **WHEN** primeira requisição `POST /orders` com Idempotency-Key K1 foi executada; segunda com mesmo K1 mas corpo diferente (ex.: `addressId` ou `expectedTotalCost` diferentes)
- **THEN** sistema retorna status `422` com erro `IDEMPOTENCY_KEY_REUSED`

#### Scenario: Idempotência em voo
- **WHEN** primeira requisição `POST /orders` com Idempotency-Key K1 está em processamento (marcado `IN_FLIGHT`); segunda requisição com mesmo K1 é recebida
- **THEN** sistema retorna status `409` com erro `IDEMPOTENCY_IN_FLIGHT` e header `Retry-After: 1` (segundos)

#### Scenario: Idempotency-Key ausente
- **WHEN** `POST /orders` não inclui header `Idempotency-Key`
- **THEN** sistema retorna status `400` com erro `IDEMPOTENCY_KEY_REQUIRED`

#### Scenario: Idempotency-Key não-UUID
- **WHEN** `POST /orders` inclui `Idempotency-Key: abc-123` (formato inválido)
- **THEN** sistema retorna status `400` com erro `IDEMPOTENCY_KEY_INVALID`

#### Scenario: Redis indisponível
- **WHEN** Redis está fora mas POST /orders é executado
- **THEN** sistema loga WARN, segue sem idempotência (sem retornar erro)

#### Scenario: Preço mudou desde a hidratação do carrinho
- **WHEN** carrinho foi hidratado com produto a R$ 100; checkout valida preço e encontra R$ 120 no `inventory`
- **THEN** sistema retorna status `409` com erro `PRICE_CHANGED` sem criar pedido

#### Scenario: Estoque insuficiente na revalidação
- **WHEN** carrinho contém 5 unidades de produto; revalidação no `inventory` mostra apenas 3 disponíveis
- **THEN** sistema retorna status `409` com erro `INSUFFICIENT_STOCK` sem criar pedido

#### Scenario: Produto removido entre hidratação e checkout
- **WHEN** carrinho contém produto; revalidação no `inventory` retorna `active: false`
- **THEN** sistema retorna status `422` com erro `PRODUCT_UNAVAILABLE` sem criar pedido

#### Scenario: Endereço não pertence ao usuário
- **WHEN** `POST /orders` com `addressId: 999` que não é endereço do usuário autenticado (validado em `user GET /internal/addresses/{id}?userId=`)
- **THEN** sistema retorna status `404`

#### Scenario: Endereço não existe
- **WHEN** `POST /orders` com `addressId` que não existe no `user`
- **THEN** sistema retorna status `404`

#### Scenario: Endereço foi removido após checkout
- **WHEN** endereço foi soft-deleted após criação do carrinho; revalidação no `user` retorna endereço removido
- **THEN** sistema aceita o pedido (rota interna `/internal/addresses/` enxerga removidos) e grava `id_address` normalmente

#### Scenario: CEP não está geocodificado
- **WHEN** `shipment` não consegue calcular frete porque CEP do endereço não existe em BrasilAPI
- **THEN** sistema retorna status `422` com erro `ZIPCODE_NOT_GEOCODED` sem criar pedido

#### Scenario: Inventory indisponível sem criar pedido
- **WHEN** `inventory` retorna `503` ou `5xx` ao validar produtos
- **THEN** sistema retorna status `503` sem gravar pedido; cliente pode retry

#### Scenario: Inventory timeout
- **WHEN** chamada `GET /internal/products` no `inventory` ultrapassa timeout de 3 segundos
- **THEN** sistema retorna status `504` sem gravar pedido

#### Scenario: User service indisponível
- **WHEN** `user` retorna `503` ao validar endereço
- **THEN** sistema retorna status `503` sem gravar pedido

#### Scenario: Shipment indisponível
- **WHEN** `shipment` retorna `503` ao calcular frete
- **THEN** sistema retorna status `503` sem gravar pedido

#### Scenario: Resposta inválida do downstream
- **WHEN** `inventory`, `user` ou `shipment` retorna JSON mal-formado ou estrutura inesperada
- **THEN** sistema retorna status `502` com erro `SERVICE_RESPONSE_INVALID`

#### Scenario: Endereço ausente no request
- **WHEN** `POST /orders` não inclui `addressId`
- **THEN** sistema retorna status `400`

#### Scenario: expectedTotalCost divergente
- **WHEN** `POST /orders` envia `expectedTotalCost: "700.00"` mas cálculo real é `724.80`
- **THEN** sistema retorna status `409` com erro `PRICE_CHANGED` sem criar pedido

#### Scenario: expectedTotalCost convergente
- **WHEN** `POST /orders` envia `expectedTotalCost: "724.80"` e cálculo real é `724.80`
- **THEN** sistema segue normalmente e cria o pedido

#### Scenario: expectedTotalCost opcional
- **WHEN** `POST /orders` não inclui `expectedTotalCost`
- **THEN** sistema segue sem validação de divergência (campo é opcional)

#### Scenario: Mais de 10 checkouts em 5 minutos
- **WHEN** usuário executa mais de 10 checkouts em 5 minutos
- **THEN** sistema retorna status `429` (rate limit)

#### Scenario: Sem escopo de escrita
- **WHEN** token não contém escopo `orders:write`
- **THEN** sistema retorna status `403`

#### Scenario: Content-Type inválido
- **WHEN** `POST /orders` com `Content-Type: text/plain`
- **THEN** sistema retorna status `415`

---

### Requirement: Snapshots de produto e frete no pedido
O sistema SHALL gravar snapshots (cópia imutável) de cada item no momento do checkout: nome, photo URL e preço revisado, garantindo histórico correto mesmo se produto ou preço mudarem depois.

#### Scenario: Snapshot de nome do produto
- **WHEN** checkout confirma pedido com produto de nome "Teclado mecânico ABNT2"
- **THEN** sistema grava `order_item.product_name = "Teclado mecânico ABNT2"` (snapshot)

#### Scenario: Snapshot de preço revisado
- **WHEN** checkout revalida e confirma preço de R$ 349.90 para produto
- **THEN** sistema grava `order_item.price_at_time = 349.90` (snapshot do preço do momento)

#### Scenario: Snapshot de foto
- **WHEN** checkout confirma pedido com produto de photoUrl "https://cdn.loja.dev/p/118/0.webp"
- **THEN** sistema grava `order_item.product_photo_url = "https://cdn.loja.dev/p/118/0.webp"` (snapshot)

#### Scenario: Frete recalculado no checkout
- **WHEN** carrinho é hidratado; usuário avalia frete em uma tela; checkout recalcula frete (nunca usa o antigo)
- **THEN** sistema usa novo frete calculado no `shipment`, nunca o que cliente viu antes

---

### Requirement: Reserva de estoque assíncrona com publicação de order.created
O sistema SHALL gravar o pedido em status `pending` (indicando reserva pendente) e publicar `ecommerce.order.created.v1` para o `inventory` processar assincronamente. Resposta `201` significa "pedido registrado", não "estoque garantido".

#### Scenario: Pedido gravado como pending
- **WHEN** checkout bem-sucedido retorna `201`
- **THEN** pedido é gravado com `status = "pending"` e `stock_reservation = "pending"`

#### Scenario: Ordem de eventos publicada
- **WHEN** pedido é criado em transação atômica
- **THEN** `order.created` é publicado na outbox dentro da mesma transação

#### Scenario: Carrinho esvaziado atomicamente
- **WHEN** pedido é criado com sucesso
- **THEN** carrinho do usuário é esvaziado na mesma transação do `POST /orders`
