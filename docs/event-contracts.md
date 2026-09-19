# Contratos de Eventos — Loja Event-Driven

> Documento de arquitetura. Define **toda** mensagem Kafka trocada entre os serviços: tópico,
> key, alias de tipo, payload, quem publica, quem consome, o efeito em cada consumidor e todos
> os resultados possíveis do consumo — o equivalente, para eventos, da tabela de códigos HTTP
> de `docs/api-contracts.md`.
>
> Configuração de broker, serializer e troubleshooting: `docs/kafka.md`.
>
> Status de cada evento:
> - `IMPLEMENTADO` — produtor e consumidor existem no código
> - `PLANEJADO` — desenhado aqui, ainda não escrito
>
> Última revisão: 2026-09-18

---

## Sumário

1. [Mapa de mensagens](#1-mapa-de-mensagens)
2. [Convenções](#2-convenções)
3. [Máquinas de estado que os eventos movem](#3-máquinas-de-estado-que-os-eventos-movem)
4. [Eventos publicados pelo `order`](#4-eventos-publicados-pelo-order)
5. [Eventos publicados pelo `inventory`](#5-eventos-publicados-pelo-inventory)
6. [Eventos publicados pelo `payment`](#6-eventos-publicados-pelo-payment)
7. [Eventos publicados pelo `shipment`](#7-eventos-publicados-pelo-shipment)
8. [Eventos publicados pelo `user`](#8-eventos-publicados-pelo-user)
9. [Índice por consumidor](#9-índice-por-consumidor)
10. [Sagas ponta a ponta](#10-sagas-ponta-a-ponta)
11. [Falhas, retry e DLT](#11-falhas-retry-e-dlt)
12. [O que mudou em relação ao desenho anterior](#12-o-que-mudou-em-relação-ao-desenho-anterior)
13. [Pendências e ordem de implementação](#13-pendências-e-ordem-de-implementação)

---

## 1. Mapa de mensagens

```
                    order.created ─────────────────────────────►
                  ◄──────────────────── stock.reserved | stock.rejected
                    order.paid ────────────────────────────────►   INVENTORY
                  ◄──────────────────── stock.committed | stock.commit.failed
                    order.cancelled ───────────────────────────►   (e SHIPMENT)
                    order.delivered ───────────────────────────►
        ORDER
   (orquestrador)   order.confirmed ───────────────────────────►
                  ◄──────────────────── shipment.status.changed     SHIPMENT

                    order.refund.requested ────────────────────►
                  ◄──────────────────── payment.approved | failed | refunded   PAYMENT

   USER ──── user.deleted ─────────────────────────────────────►   INVENTORY, ORDER
```

**O `order` é o orquestrador.** Todo evento de outro serviço termina no `order`, e é o `order`
que decide o passo seguinte. `inventory`, `payment` e `shipment` nunca reagem a eventos uns dos
outros. A razão é prática: a máquina de estados do pedido fica num lugar só, e depurar uma saga
é ler o log de um serviço, não costurar o de quatro.

A exceção é `user.deleted`, que não faz parte de saga nenhuma: é um aviso de que dados pessoais
precisam sair.

### 1.1 Catálogo

| # | Tópico | Alias | Produtor | Consumidores | Status |
|---|---|---|---|---|---|
| 1 | `ecommerce.order.created.v1` | `orderCreated` | order | inventory | `IMPLEMENTADO` |
| 2 | `ecommerce.order.cancelled.v1` | `orderCancelled` | order | inventory, shipment | `IMPLEMENTADO` (inventory) · `PLANEJADO` (shipment) |
| 3 | `ecommerce.order.paid.v1` | `orderPaid` | order | inventory | `PLANEJADO` |
| 4 | `ecommerce.order.confirmed.v1` | `orderConfirmed` | order | shipment | `PLANEJADO` |
| 5 | `ecommerce.order.delivered.v1` | `orderDelivered` | order | inventory | `PLANEJADO` |
| 6 | `ecommerce.order.refund.requested.v1` | `orderRefundRequested` | order | payment | `PLANEJADO` |
| 7 | `ecommerce.stock.reserved.v1` | `stockReserved` | inventory | order | `IMPLEMENTADO` |
| 8 | `ecommerce.stock.rejected.v1` | `stockRejected` | inventory | order | `IMPLEMENTADO` |
| 9 | `ecommerce.stock.committed.v1` | `stockCommitted` | inventory | order | `PLANEJADO` |
| 10 | `ecommerce.stock.commit.failed.v1` | `stockCommitFailed` | inventory | order | `PLANEJADO` |
| 11 | `ecommerce.payment.approved.v1` | `paymentApproved` | payment | order | `PLANEJADO` |
| 12 | `ecommerce.payment.failed.v1` | `paymentFailed` | payment | order | `PLANEJADO` |
| 13 | `ecommerce.payment.refunded.v1` | `paymentRefunded` | payment | order | `PLANEJADO` |
| 14 | `ecommerce.shipment.status.changed.v1` | `shipmentStatusChanged` | shipment | order | `PLANEJADO` |
| 15 | `ecommerce.user.deleted.v1` | `userDeleted` | user | inventory, order | `PLANEJADO` |

---

## 2. Convenções

### 2.1 Nome do tópico

`ecommerce.<agregado>.<fato-no-passado>.v<versão>`. O nome descreve algo que **já aconteceu**
(`order.paid`, não `pay.order`). Até o pedido de estorno é um fato: "o estorno foi pedido".

### 2.2 Key e ordem

| Tópico | Key |
|---|---|
| todos os de pedido, estoque, pagamento e envio | `orderId` (string) |
| `user.deleted` | `userId` (string) |

A key garante ordem **dentro de um tópico** para o mesmo pedido: `in_transit` chega antes de
`delivered` no tópico `shipment.status.changed`.

**Não existe ordem entre tópicos diferentes.** `order.cancelled` e `order.paid` do mesmo pedido
podem ser consumidos em qualquer ordem pelo `inventory`. Por isso nenhum consumidor decide
pela ordem de chegada: cada um decide pelo **estado atual** do que é dele (status da reserva,
status do pedido, status do pagamento).

### 2.3 Envelope

Todo evento é um `record` achatado que começa com os mesmos dois campos:

| Campo | Tipo | Regra |
|---|---|---|
| `eventId` | string, UUID v4 | único por publicação. Uma republicação gera `eventId` novo |
| `producedAt` | instant ISO-8601 UTC | momento em que o evento foi gravado na `outbox`, dentro da transação (§2.6) |

Os demais campos são do evento. Não há envelope aninhado (`{ "meta": …, "data": … }`): os
records que já existem no código são planos, e este documento segue o código.

> Como republicar gera `eventId` novo, **nenhum consumidor deduplica por `eventId`**. A
> idempotência vem do estado de negócio: "a reserva já está `confirmed`", "o pedido já saiu de
> `pending`". É mais robusto — funciona também para o evento republicado de propósito.

### 2.4 Serialização

- Produtor: o record é serializado pelo `JsonMapper` do Spring Boot na coluna `payload` da
  `outbox`. Consumidor: `JacksonJsonDeserializer` atrás de `ErrorHandlingDeserializer`
  (ver `docs/kafka.md` §2).
- O tipo viaja no header `__TypeId__` como **alias** (`orderCreated`), nunca como FQCN. O
  produtor grava o alias na coluna `type` da `outbox` e o Debezium o coloca no header; o
  consumidor mapeia o alias para o seu próprio record em
  `spring.kafka.consumer.properties.spring.json.type.mapping` (§9 lista as linhas).
- Dinheiro: número JSON decimal (`724.80`), lido como `BigDecimal`. Nunca `double`.
- Datas: ISO-8601 UTC com `Z`.
- Ids: número JSON.

Cada serviço tem **a sua cópia** do record, em `infra/outbound/messaging/events` no produtor e
`infra/inbound/messaging/events` no consumidor. Não há módulo de contratos compartilhado: o
contrato é este documento.

### 2.5 Evolução

| Mudança | Pode no mesmo tópico? |
|---|---|
| Adicionar campo opcional | **sim** — o deserializer do spring-kafka ignora campo desconhecido |
| Remover campo, renomear, mudar tipo, tornar obrigatório | **não** — tópico `.v2`, publicado em paralelo ao `.v1` até todos os consumidores migrarem |

### 2.6 Entrega

- **At-least-once.** Rebalance, retry e redeploy entregam a mesma mensagem mais de uma vez.
  Todo consumidor é idempotente; a coluna "ignorado" das tabelas de cada evento diz como.
- **Publicação pela outbox** ([ADR-001](outbox-debezium.md)): o serviço grava o evento na
  tabela `outbox` **na mesma transação** da mudança de estado, e o Debezium o publica lendo o
  WAL. O evento sai se, e somente se, o estado foi commitado, e sai na ordem de commit.
  Nenhum serviço publica evento de domínio com `KafkaTemplate`.
  *Até a fase 4 do ADR, `order` e `inventory` ainda publicam com `KafkaTemplate` depois do
  commit — ver §13.1, item 1.*
- **Grupo de consumo = nome do serviço** (`inventory`, `order`…). Duas instâncias do mesmo
  serviço dividem partições; serviços diferentes recebem cópias.

### 2.7 Resultados de consumo

O equivalente dos códigos HTTP. Toda tabela "Consumo" deste documento usa estes resultados:

| Resultado | Significado | Offset |
|---|---|---|
| `processado` | efeito aplicado; pode publicar o próximo evento | commitado |
| `recusado` | uma regra de negócio impediu o efeito, e a recusa é **respondida** com outro evento (ex.: `stock.rejected`) | commitado |
| `ignorado` | reentrega, ou o estado local já está à frente; nada muda | commitado |
| `retry` | falha transitória: banco fora, lock, HTTP 5xx ou timeout de outro serviço | **não** commitado; reprocessa com backoff (§11) |
| `DLT` | falha permanente: payload inválido, invariante quebrado, 4xx definitivo | enviado para `<tópico>-dlt` e commitado; exige humano |

---

## 3. Máquinas de estado que os eventos movem

### 3.1 Pedido (`order_status`)

O status **só anda para a frente**. Evento atrasado que faria o pedido voltar é `ignorado`.

| De | Para | Gatilho | Publica |
|---|---|---|---|
| — | `pending` | `POST /orders` | `order.created` |
| `pending` | `paid` | `payment.approved` | `order.paid` |
| `pending` | `cancelled` | `stock.rejected` · TTL do checkout · `POST /orders/{id}/cancel` | `order.cancelled` |
| `paid` | `processing` | `stock.committed` | `order.confirmed` |
| `paid` | `cancelled` | `stock.commit.failed` · `POST /orders/{id}/cancel` | `order.cancelled` + `order.refund.requested` |
| `processing` | `cancelled` | `POST /orders/{id}/cancel` · `shipment.status.changed` → `cancelled` | `order.cancelled` + `order.refund.requested` |
| `processing` | `shipped` | `shipment.status.changed` → `in_transit` | — |
| `processing`, `shipped` | `delivered` | `shipment.status.changed` → `delivered` | `order.delivered` |
| `cancelled`, `shipped`, `delivered` | `refunded` | `payment.refunded` com `full: true` | — |

`processing → delivered` pulando `shipped` é permitido de propósito: se o evento de
`in_transit` for para a DLT, a entrega confirmada pelo comprador não pode ficar presa atrás dele.

### 3.2 Reserva de estoque (`reservation_status`)

| De | Para | Gatilho | Efeito em `product.stock` |
|---|---|---|---|
| — | `held` | `order.created` | nenhum — só deixa de estar disponível |
| `held` | `confirmed` | `order.paid` | `stock -= quantity` |
| `held` | `released` | `order.cancelled` · `stock.commit.failed` | nenhum |
| `confirmed` | `released` | `order.cancelled` (pedido cancelado depois da baixa) | `stock += quantity` |

`held` vencido continua `held` na tabela; ele só para de contar na disponibilidade
(`V2__stock_reservation.sql`). Vencer não é perder: um `order.paid` que chega depois ainda
consegue confirmar se houver disponível (§5 → `order.paid`).

### 3.3 Pagamento e envio

- Pagamento: mapeamento de status do Mercado Pago em `docs/api-contracts.md` §9.2.
- Envio: ciclo e quem move cada transição em `docs/api-contracts.md` §10.2.

---

## 4. Eventos publicados pelo `order`

### 4.1 `ecommerce.order.created.v1` — `IMPLEMENTADO`

Pedido gravado em `pending`. Pede a reserva do estoque.

| | |
|---|---|
| **Alias** | `orderCreated` |
| **Key** | `orderId` |
| **Publicado por** | `OrderEventPublisherPort.publishOrderCreated`, na transação do `POST /orders` |
| **Consumido por** | `inventory` — `OrderCreatedConsumer` → `ReserveStockService` |

**Payload**

```json
{
  "eventId": "5c1e8a4e-2f6b-4b8e-9a51-0d6c3f1e7a90",
  "producedAt": "2026-09-17T14:00:00.412Z",
  "orderId": 3301,
  "customerId": 42,
  "total": 724.80,
  "items": [
    { "productId": 118, "quantity": 2 }
  ]
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | também é a key |
| `customerId` | long | sim | |
| `total` | decimal | sim | `orders.total_cost`. O `inventory` não usa; fica para auditoria |
| `items` | array | sim, ≥ 1 | |
| `items[].productId` | long | sim | |
| `items[].quantity` | int | sim, > 0 | |

**Consumo no `inventory`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | todos os itens têm disponível | uma linha `held` por item, `expires_at = now + 30 min`; publica `stock.reserved` |
| `recusado` | algum item sem disponível, ou produto inexistente | rollback de todas as linhas do pedido; publica `stock.rejected` com o primeiro `productId` que faltou (itens processados em ordem de id, para evitar deadlock) |
| `ignorado` | já existe reserva do pedido (reentrega) | nada. Com a outbox, o `stock.reserved` da primeira entrega foi gravado junto com as reservas: não existe reserva sem evento |
| `retry` | banco indisponível, timeout de lock | |
| `DLT` | JSON inválido; `items` vazio; `quantity` ≤ 0 | |

---

### 4.2 `ecommerce.order.cancelled.v1` — `IMPLEMENTADO` (inventory) · `PLANEJADO` (shipment)

O pedido foi para `cancelled`, por qualquer motivo. É o sinal para devolver o que o pedido
segurava: estoque e envio. **Não** devolve dinheiro — isso é `order.refund.requested` (§4.6).

| | |
|---|---|
| **Alias** | `orderCancelled` |
| **Key** | `orderId` |
| **Publicado por** | `OrderCancellationTransaction`, na mesma transação da transição |
| **Consumido por** | `inventory` — `OrderCancelledConsumer` → `ReleaseReservationService` · `shipment` — `OrderCancelledConsumer` (planejado) |

**Quando é publicado**

| Origem | Status de onde sai | Status |
|---|---|---|
| `stock.rejected` | `pending` | `IMPLEMENTADO` |
| TTL do checkout (PIX não pago) | `pending` | `PLANEJADO` |
| `POST /orders/{id}/cancel` | `pending`, `paid`, `processing` | `PLANEJADO` |
| `stock.commit.failed` | `paid` | `PLANEJADO` |
| `shipment.status.changed` → `cancelled` | `processing` | `PLANEJADO` |

**Payload**

```json
{
  "eventId": "0b7f3d2a-8c4e-4f1a-b6d9-2e5a7c9f1b34",
  "producedAt": "2026-09-17T14:30:01.090Z",
  "orderId": 3301,
  "customerId": 42,
  "reason": "estoque indisponivel para o produto 118"
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | |
| `customerId` | long | sim | hoje pode sair `null` se o pedido sumiu entre o commit e a leitura (`CancelOrderService`) |
| `reason` | string | sim | texto para humano; nenhum consumidor ramifica por ele |

**Consumo no `inventory`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | há reserva `held` | `held` → `released` — `IMPLEMENTADO` |
| `processado` | reserva já `confirmed` (cancelado depois da baixa) | `confirmed` → `released` e `stock += quantity` — `PLANEJADO`; hoje o filtro por status protege `confirmed` e o estoque **não volta** |
| `ignorado` | sem reserva, ou já `released` | nada |
| `retry` | banco | |
| `DLT` | payload inválido | |

**Consumo no `shipment`** — `PLANEJADO`

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | envio `pending` ou `ready_to_ship` | → `cancelled`; publica `shipment.status.changed` (`cancelled`), que o `order` recebe como eco e ignora |
| `ignorado` | pedido ainda sem envio, ou envio já `cancelled` | nada |
| `recusado` | envio `in_transit` ou além | nada muda; log `WARN`. A mercadoria já saiu — é devolução, e a loja resolve. Não publica nada |
| `retry` | banco | |
| `DLT` | payload inválido | |

---

### 4.3 `ecommerce.order.paid.v1` — `PLANEJADO`

O pagamento foi aprovado e o pedido foi para `paid`. Pede a **baixa** do estoque.

| | |
|---|---|
| **Alias** | `orderPaid` |
| **Key** | `orderId` |
| **Publicado por** | `order`, ao consumir `payment.approved` com o pedido em `pending` |
| **Consumido por** | `inventory` — `OrderPaidConsumer` → `CommitStockService` |

**Payload**

```json
{
  "eventId": "7d2c9e1f-4a3b-4c8d-9e6f-1a2b3c4d5e6f",
  "producedAt": "2026-09-17T14:06:32.118Z",
  "orderId": 3301,
  "customerId": 42,
  "paymentId": 9901,
  "items": [
    { "productId": 118, "quantity": 2 }
  ]
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | |
| `customerId` | long | sim | |
| `paymentId` | long | sim | auditoria |
| `items` | array | sim, ≥ 1 | mesmos itens do `order.created` |

`items` vai de novo, mesmo já estando nas reservas, para o `inventory` conseguir baixar o
estoque **sem depender da reserva ter existido** — se o `order.created` foi para a DLT, o
pagamento aprovado ainda assim fecha.

**Consumo no `inventory`**

Tudo ou nada: se um item falha, nenhum é baixado.

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | reservas `held` dentro do prazo | `held` → `confirmed`, `stock -= quantity`; publica `stock.committed` |
| `processado` | reserva `held` **vencida**, mas ainda há disponível | confirma e baixa do mesmo jeito; publica `stock.committed` |
| `processado` | pedido sem reserva nenhuma, e há disponível | cria as linhas já `confirmed` e baixa; publica `stock.committed` |
| `recusado` | falta disponível para algum item; ou reserva `released` (pedido cancelado antes) | nada é baixado; reservas `held` do pedido → `released`; publica `stock.commit.failed` |
| `ignorado` | reservas já `confirmed` (reentrega) | nada — a baixa e o `stock.committed` foram gravados na mesma transação da primeira vez |
| `retry` | banco, timeout de lock | |
| `DLT` | payload inválido; `items` vazio | |

---

### 4.4 `ecommerce.order.confirmed.v1` — `PLANEJADO`

Pedido pago **e** com estoque baixado (`processing`). É o único gatilho que cria um envio.

| | |
|---|---|
| **Alias** | `orderConfirmed` |
| **Key** | `orderId` |
| **Publicado por** | `order`, ao consumir `stock.committed` com o pedido em `paid` |
| **Consumido por** | `shipment` — `OrderConfirmedConsumer` → `CreateShipmentService` |

**Payload**

```json
{
  "eventId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "producedAt": "2026-09-17T14:06:33.004Z",
  "orderId": 3301,
  "customerId": 42,
  "addressId": 15,
  "freightCost": 25.00
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | |
| `customerId` | long | sim | vira `shipment.id_user`; também é o `userId` da busca do endereço |
| `addressId` | long | sim | `orders.id_address` |
| `freightCost` | decimal | sim | `orders.freight_cost`, copiado para `shipment.freight_tax` **sem recalcular** |

**Consumo no `shipment`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | não há envio do pedido | busca o destino em `GET /internal/addresses/{addressId}?userId={customerId}`, copia para `to_*`; origem da configuração da loja para `from_*`; grava `pending`; publica `shipment.status.changed` (`null` → `pending`) |
| `ignorado` | já existe envio do pedido (`shipment_order_uk`) | nada |
| `retry` | `user` indisponível ou timeout; banco | |
| `DLT` | endereço `404` — removido pelo cliente depois do checkout (§13.2, item 8); payload inválido | |

---

### 4.5 `ecommerce.order.delivered.v1` — `PLANEJADO`

O comprador confirmou a entrega. Concede o direito de avaliar.

| | |
|---|---|
| **Alias** | `orderDelivered` |
| **Key** | `orderId` |
| **Publicado por** | `order`, ao consumir `shipment.status.changed` → `delivered` |
| **Consumido por** | `inventory` — `OrderDeliveredConsumer` → `GrantReviewEligibilityService` (módulo `review`) |

**Payload**

```json
{
  "eventId": "c3d4e5f6-a7b8-4c9d-8e0f-1a2b3c4d5e6f",
  "producedAt": "2026-09-22T16:20:00.551Z",
  "orderId": 3301,
  "customerId": 42,
  "deliveredAt": "2026-09-22T16:18:00Z",
  "items": [
    { "productId": 118 }
  ]
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | |
| `customerId` | long | sim | vira `review_eligibility.id_user` |
| `deliveredAt` | instant | sim | quando o comprador confirmou, não quando o evento saiu |
| `items[].productId` | long | sim, ≥ 1 | um produto por linha, sem repetição |

**Consumo no `inventory`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | — | uma linha em `review_eligibility (id_user, id_product, id_order)` por item |
| `ignorado` | linhas já existem (reentrega) | `INSERT … ON CONFLICT DO NOTHING` sobre a PK composta |
| `retry` | banco | |
| `DLT` | payload inválido | |

Produto removido depois da compra ainda recebe a elegibilidade; é o `POST /products/{id}/reviews`
que devolve `404`. O evento não tem por que saber disso.

---

### 4.6 `ecommerce.order.refund.requested.v1` — `PLANEJADO`

Um pagamento capturado precisa ser devolvido. **Único caminho automático de estorno.**

| | |
|---|---|
| **Alias** | `orderRefundRequested` |
| **Key** | `orderId` |
| **Publicado por** | `order` |
| **Consumido por** | `payment` — `OrderRefundRequestedConsumer` → `RefundPaymentService` |

**Quando é publicado**

| Situação | Estado do pedido |
|---|---|
| Pedido pago cancelado (cliente, loja, `stock.commit.failed`, envio cancelado) | sai de `paid`/`processing` para `cancelled` |
| Pagamento aprovado chega com o pedido já `cancelled` (PIX pago depois do TTL) | continua `cancelled` |
| Segundo pagamento aprovado para um pedido já pago | continua onde estava |

**Payload**

```json
{
  "eventId": "e5f6a7b8-c9d0-4e1f-8a2b-3c4d5e6f7a8b",
  "producedAt": "2026-09-17T14:31:00.200Z",
  "orderId": 3301,
  "paymentId": 9901,
  "reason": "estoque indisponivel apos pagamento: produto 118"
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | |
| `paymentId` | long | sim | **qual** pagamento devolver — no pagamento em dobro, o pedido tem dois |
| `reason` | string | sim | vai para o log e para a descrição do estorno |

Sempre estorno **total** daquele pagamento. Estorno parcial é decisão humana e sai por
`POST /payments/{id}/refund`.

**Consumo no `payment`**

O estorno vai ao Mercado Pago com `X-Idempotency-Key: refund-{paymentId}`. A chave é
determinística de propósito: reentrega do evento, retry e republicação chegam ao MP com a mesma
chave, e o MP não estorna duas vezes.

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | pagamento `captured` | estorna no MP, grava `refunded`; publica `payment.refunded` |
| `ignorado` | já `refunded` | nada |
| `ignorado` | pagamento não está `captured` (`pending`, `failed`, `cancelled`) | log `WARN` — não há dinheiro a devolver |
| `retry` | MP `5xx`, `429` ou timeout; banco | |
| `DLT` | MP `4xx` definitivo (fora da janela de 180 dias, saldo insuficiente na conta da loja); `paymentId` inexistente ou de outro `orderId` | a loja estorna manualmente |

---

## 5. Eventos publicados pelo `inventory`

### 5.1 `ecommerce.stock.reserved.v1` — `IMPLEMENTADO`

Todos os itens do pedido estão reservados por 30 min.

| | |
|---|---|
| **Alias** | `stockReserved` |
| **Key** | `orderId` |
| **Publicado por** | `StockEventPublisherPort.publishStockReserved`, via `ReserveStockService` |
| **Consumido por** | `order` — `StockEventsConsumer.onStockReserved` |

**Payload**

```json
{
  "eventId": "1f2e3d4c-5b6a-4798-8a6b-5c4d3e2f1a0b",
  "producedAt": "2026-09-17T14:00:00.803Z",
  "orderId": 3301
}
```

**Consumo no `order`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | pedido `pending` | hoje só loga (`IMPLEMENTADO`). Planejado: projeção `stockReservation = reserved`, exibida no `GET /orders/{id}` |
| `ignorado` | pedido não está mais `pending` | nada |
| `DLT` | payload inválido | |

---

### 5.2 `ecommerce.stock.rejected.v1` — `IMPLEMENTADO`

Faltou estoque na reserva. Nada foi reservado.

| | |
|---|---|
| **Alias** | `stockRejected` |
| **Key** | `orderId` |
| **Publicado por** | `StockEventPublisherPort.publishStockRejected`, via `ReserveStockService` |
| **Consumido por** | `order` — `StockEventsConsumer.onStockRejected` → `CancelOrderService` |

**Payload**

```json
{
  "eventId": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
  "producedAt": "2026-09-17T14:00:00.790Z",
  "orderId": 3301,
  "productId": 118
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | |
| `productId` | long | sim | o **primeiro** produto sem disponível, em ordem de id — não a lista completa |

**Consumo no `order`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | pedido `pending` | → `cancelled` com `reason = "estoque indisponivel para o produto {id}"`; publica `order.cancelled` |
| `ignorado` | pedido não está `pending` | `CancelOrderService` devolve `NOT_APPLICABLE` e não publica nada |
| `retry` | banco | |
| `DLT` | payload inválido | |

---

### 5.3 `ecommerce.stock.committed.v1` — `PLANEJADO`

O estoque do pedido foi baixado.

| | |
|---|---|
| **Alias** | `stockCommitted` |
| **Key** | `orderId` |
| **Publicado por** | `inventory` — `CommitStockService`, ao consumir `order.paid` |
| **Consumido por** | `order` — `StockEventsConsumer.onStockCommitted` |

**Payload**

```json
{
  "eventId": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
  "producedAt": "2026-09-17T14:06:32.640Z",
  "orderId": 3301
}
```

**Consumo no `order`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | pedido `paid` | → `processing`; publica `order.confirmed` |
| `ignorado` | pedido já `processing` ou além (reentrega) | nada |
| `ignorado` | pedido `cancelled` (cancelado enquanto a baixa acontecia) | nada — o `order.cancelled` que já saiu faz o `inventory` devolver o estoque (`confirmed` → `released`), em qualquer ordem que os dois cheguem |
| `retry` | banco | |
| `DLT` | payload inválido | |

---

### 5.4 `ecommerce.stock.commit.failed.v1` — `PLANEJADO`

O pagamento foi aprovado, mas o estoque não pôde ser baixado.

| | |
|---|---|
| **Alias** | `stockCommitFailed` |
| **Key** | `orderId` |
| **Publicado por** | `inventory` — `CommitStockService` |
| **Consumido por** | `order` — `StockEventsConsumer.onStockCommitFailed` |

**Payload**

```json
{
  "eventId": "3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f",
  "producedAt": "2026-09-17T14:36:10.004Z",
  "orderId": 3301,
  "productId": 118,
  "reason": "EXPIRED_WITHOUT_STOCK"
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | |
| `productId` | long | sim | primeiro produto que faltou |
| `reason` | enum | sim | `EXPIRED_WITHOUT_STOCK` — a reserva venceu e o disponível acabou · `RESERVATION_RELEASED` — a reserva já tinha sido liberada |

**Consumo no `order`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | pedido `paid` | → `cancelled`; publica `order.cancelled` e `order.refund.requested` — o cliente pagou e não vai receber, então o dinheiro volta sozinho |
| `ignorado` | pedido já `cancelled` | nada — o caminho que cancelou já pediu o estorno |
| `retry` | banco | |
| `DLT` | payload inválido | |

---

## 6. Eventos publicados pelo `payment`

Todos nascem do webhook do Mercado Pago ou do `POST /payments/{id}/sync` — sempre depois de
reler o pagamento no MP. O corpo do webhook nunca é fonte de verdade
(`docs/api-contracts.md` §9.4).

### 6.1 `ecommerce.payment.approved.v1` — `PLANEJADO`

| | |
|---|---|
| **Alias** | `paymentApproved` |
| **Key** | `orderId` |
| **Publicado por** | `payment`, quando o status local vai para `captured` |
| **Consumido por** | `order` — `PaymentEventsConsumer.onPaymentApproved` |

**Payload**

```json
{
  "eventId": "4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f8a",
  "producedAt": "2026-09-17T14:06:31.877Z",
  "orderId": 3301,
  "paymentId": 9901,
  "externalId": "119384756201",
  "amount": 724.80,
  "method": "pix",
  "approvedAt": "2026-09-17T14:06:29Z"
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `orderId` | long | sim | |
| `paymentId` | long | sim | |
| `externalId` | string | sim | id do pagamento no MP |
| `amount` | decimal | sim | valor efetivamente capturado |
| `method` | enum | sim | `pix` \| `credit_card` \| `checkout_pro` |
| `approvedAt` | instant | sim | `date_approved` do MP |

**Consumo no `order`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | pedido `pending` e `amount == total_cost` | → `paid`; guarda `paymentId`; publica `order.paid` |
| `recusado` | pedido `cancelled` (pago depois do TTL) | status não muda; publica `order.refund.requested` para este `paymentId` |
| `recusado` | pedido já pago por **outro** `paymentId` (pagamento em dobro) | status não muda; publica `order.refund.requested` para o segundo |
| `ignorado` | pedido já pago por **este** `paymentId` (reentrega) | nada |
| `retry` | banco | |
| `DLT` | `amount ≠ total_cost`; pedido inexistente; payload inválido | dinheiro divergente não se resolve sozinho |

---

### 6.2 `ecommerce.payment.failed.v1` — `PLANEJADO`

O MP recusou ou cancelou a cobrança.

| | |
|---|---|
| **Alias** | `paymentFailed` |
| **Key** | `orderId` |
| **Publicado por** | `payment`, quando o status local vai para `failed` ou `cancelled` |
| **Consumido por** | `order` — `PaymentEventsConsumer.onPaymentFailed` |

**Payload**

```json
{
  "eventId": "5e6f7a8b-9c0d-4e1f-8a2b-4c5d6e7f8a9b",
  "producedAt": "2026-09-17T14:03:15.310Z",
  "orderId": 3301,
  "paymentId": 9903,
  "status": "failed",
  "statusDetail": "cc_rejected_insufficient_amount"
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `status` | enum | sim | `failed` \| `cancelled` |
| `statusDetail` | string | sim | `status_detail` do MP, repassado sem tradução |

**Consumo no `order`**

O pedido **não muda de status**: o cliente pode tentar outro pagamento enquanto a reserva não
vence. O evento só alimenta a projeção exibida no `GET /orders/{id}`.

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | pedido `pending` | projeção do pagamento atualizada |
| `ignorado` | pedido já pago por outro pagamento | a projeção não regride |
| `retry` | banco | |
| `DLT` | payload inválido | |

---

### 6.3 `ecommerce.payment.refunded.v1` — `PLANEJADO`

Dinheiro devolvido ao cliente, total ou parcialmente.

| | |
|---|---|
| **Alias** | `paymentRefunded` |
| **Key** | `orderId` |
| **Publicado por** | `payment` — depois de `order.refund.requested`, de `POST /payments/{id}/refund`, ou de webhook de `refunded`/`charged_back` |
| **Consumido por** | `order` — `PaymentEventsConsumer.onPaymentRefunded` |

**Payload**

```json
{
  "eventId": "6f7a8b9c-0d1e-4f2a-9b3c-5d6e7f8a9b0c",
  "producedAt": "2026-09-17T14:31:04.702Z",
  "orderId": 3301,
  "paymentId": 9901,
  "amount": 724.80,
  "totalRefunded": 724.80,
  "full": true,
  "origin": "requested",
  "refundedAt": "2026-09-17T14:31:03Z"
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `amount` | decimal | sim | valor **deste** estorno |
| `totalRefunded` | decimal | sim | soma de todos os estornos do pagamento |
| `full` | boolean | sim | `totalRefunded == value` do pagamento |
| `origin` | enum | sim | `requested` (veio de `order.refund.requested`) \| `manual` (`POST /payments/{id}/refund`) \| `chargeback` (contestação no cartão) |
| `refundedAt` | instant | sim | |

**Consumo no `order`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | `full: true`, pedido `cancelled`, `shipped` ou `delivered` | → `refunded` |
| `processado` | `full: true`, pedido `paid` ou `processing` (estorno manual ou chargeback antes do envio) | → `cancelled` (publica `order.cancelled`, que devolve estoque e cancela o envio) → `refunded` |
| `processado` | `full: false` | só a projeção (valor estornado); status não muda |
| `ignorado` | pedido já `refunded` | nada |
| `retry` | banco | |
| `DLT` | payload inválido; pedido inexistente | |

---

## 7. Eventos publicados pelo `shipment`

### 7.1 `ecommerce.shipment.status.changed.v1` — `PLANEJADO`

Toda transição de envio, inclusive a criação.

| | |
|---|---|
| **Alias** | `shipmentStatusChanged` |
| **Key** | `orderId` — não `shipmentId`: o que precisa de ordem é o pedido |
| **Publicado por** | `shipment`, na transação de cada transição |
| **Consumido por** | `order` — `ShipmentEventsConsumer` |

**Payload**

```json
{
  "eventId": "7a8b9c0d-1e2f-4a3b-8c4d-6e7f8a9b0c1d",
  "producedAt": "2026-09-19T08:11:00.420Z",
  "orderId": 3301,
  "shipmentId": 5501,
  "from": "ready_to_ship",
  "to": "in_transit",
  "trackingCode": "AA123456789BR",
  "reason": null,
  "changedAt": "2026-09-19T08:10:58Z"
}
```

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `shipmentId` | long | sim | |
| `from` | enum `shipment_status` | não | `null` só na criação |
| `to` | enum `shipment_status` | sim | |
| `trackingCode` | string | não | texto livre da loja |
| `reason` | string | não | preenchido em `cancelled` |
| `changedAt` | instant | sim | |

**Consumo no `order`**

| `to` | Resultado | O que acontece no pedido |
|---|---|---|
| `pending` | `processado` | projeção: guarda `shipmentId` |
| `ready_to_ship` | `processado` | projeção |
| `in_transit` | `processado` | `processing` → `shipped` |
| `out_for_delivery` | `processado` | projeção |
| `delivered` | `processado` | `processing`/`shipped` → `delivered`; publica `order.delivered` |
| `returned` | `processado` | projeção; a loja decide o estorno (`POST /payments/{id}/refund`) |
| `cancelled` | `processado` | pedido `processing` → `cancelled`; publica `order.cancelled` e `order.refund.requested` |
| `cancelled` | `ignorado` | pedido já `cancelled` — é o eco do cancelamento que partiu do próprio pedido |
| qualquer | `ignorado` | transição que faria o pedido voltar (regra do §3.1) |
| qualquer | `retry` | banco |
| qualquer | `DLT` | payload inválido; `to` fora do enum |

---

## 8. Eventos publicados pelo `user`

### 8.1 `ecommerce.user.deleted.v1` — `PLANEJADO`

O usuário removeu a conta (`DELETE /users/me`). Os outros serviços apagam ou anonimizam o que
guardam dele.

| | |
|---|---|
| **Alias** | `userDeleted` |
| **Key** | `userId` |
| **Publicado por** | `user`, na transação do soft delete |
| **Consumido por** | `inventory` — `UserDeletedConsumer` (módulo `review`) · `order` — `UserDeletedConsumer` (módulo `cart`) |

**Payload**

```json
{
  "eventId": "8b9c0d1e-2f3a-4b4c-9d5e-7f8a9b0c1d2e",
  "producedAt": "2026-10-01T12:00:00.000Z",
  "userId": 42,
  "deletedAt": "2026-10-01T11:59:59Z"
}
```

**Consumo no `inventory`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | usuário tem avaliações | `review.user_name = 'Usuário removido'`, `review.user_photo_url = NULL`. Nota e texto ficam: pertencem ao produto |
| `ignorado` | nada a anonimizar | |
| `retry` | banco | |
| `DLT` | payload inválido | |

> É a única exceção à regra "snapshot é imutável": a obrigação de apagar dado pessoal (LGPD)
> vale mais que a fidelidade histórica do nome no card da avaliação.

**Consumo no `order`**

| Resultado | Quando | O que acontece |
|---|---|---|
| `processado` | usuário tem carrinho | soft delete de `cart` e `cart_items` |
| `ignorado` | sem carrinho | |
| `retry` | banco | |
| `DLT` | payload inválido | |

Pedidos **não** são tocados: são registro fiscal, e a base legal para mantê-los é outra.

---

## 9. Índice por consumidor

O que cada serviço escuta, e a linha de `type.mapping` de consumidor que precisa ter quando
tudo estiver implementado. Grupo de consumo = nome do serviço.

Não há linha de **produtor**: o alias viaja na coluna `type` da `outbox` e o Debezium o coloca
no header `__TypeId__` (`docs/outbox-debezium.md` §6.3). Cada adaptador de publicação conhece
o tópico e o alias dos eventos que grava.

### 9.1 `inventory`

| Tópico | Consumidor | Status |
|---|---|---|
| `order.created` | `product` · `OrderCreatedConsumer` | `IMPLEMENTADO` |
| `order.cancelled` | `product` · `OrderCancelledConsumer` | `IMPLEMENTADO` (falta `confirmed` → `released`) |
| `order.paid` | `product` · `OrderPaidConsumer` | `PLANEJADO` |
| `order.delivered` | `review` · `OrderDeliveredConsumer` | `PLANEJADO` |
| `user.deleted` | `review` · `UserDeletedConsumer` | `PLANEJADO` |

```properties
spring.kafka.consumer.properties.spring.json.type.mapping=\
  orderCreated:ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderCreatedEvent,\
  orderCancelled:ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderCancelledEvent,\
  orderPaid:ecommerce_event_driven.inventory.modules.product.infra.inbound.messaging.events.OrderPaidEvent,\
  orderDelivered:ecommerce_event_driven.inventory.modules.review.infra.inbound.messaging.events.OrderDeliveredEvent,\
  userDeleted:ecommerce_event_driven.inventory.modules.review.infra.inbound.messaging.events.UserDeletedEvent
```

### 9.2 `order`

| Tópico | Consumidor | Status |
|---|---|---|
| `stock.reserved` | `order` · `StockEventsConsumer` | `IMPLEMENTADO` (só loga) |
| `stock.rejected` | `order` · `StockEventsConsumer` | `IMPLEMENTADO` |
| `stock.committed` | `order` · `StockEventsConsumer` | `PLANEJADO` |
| `stock.commit.failed` | `order` · `StockEventsConsumer` | `PLANEJADO` |
| `payment.approved` / `failed` / `refunded` | `order` · `PaymentEventsConsumer` | `PLANEJADO` |
| `shipment.status.changed` | `order` · `ShipmentEventsConsumer` | `PLANEJADO` |
| `user.deleted` | `cart` · `UserDeletedConsumer` | `PLANEJADO` |

```properties
spring.kafka.consumer.properties.spring.json.type.mapping=\
  stockReserved:ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockReservedEvent,\
  stockRejected:ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockRejectedEvent,\
  stockCommitted:ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockCommittedEvent,\
  stockCommitFailed:ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.StockCommitFailedEvent,\
  paymentApproved:ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.PaymentApprovedEvent,\
  paymentFailed:ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.PaymentFailedEvent,\
  paymentRefunded:ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.PaymentRefundedEvent,\
  shipmentStatusChanged:ecommerce_event_driven.order.modules.order.infra.inbound.messaging.events.ShipmentStatusChangedEvent,\
  userDeleted:ecommerce_event_driven.order.modules.cart.infra.inbound.messaging.events.UserDeletedEvent
```

### 9.3 `payment`

| Tópico | Consumidor | Status |
|---|---|---|
| `order.refund.requested` | `payment` · `OrderRefundRequestedConsumer` | `PLANEJADO` |

```properties
spring.kafka.consumer.properties.spring.json.type.mapping=\
  orderRefundRequested:ecommerce_event_driven.payment.modules.payment.infra.inbound.messaging.events.OrderRefundRequestedEvent
```

### 9.4 `shipment`

| Tópico | Consumidor | Status |
|---|---|---|
| `order.confirmed` | `shipment` · `OrderConfirmedConsumer` | `PLANEJADO` |
| `order.cancelled` | `shipment` · `OrderCancelledConsumer` | `PLANEJADO` |

```properties
spring.kafka.consumer.properties.spring.json.type.mapping=\
  orderConfirmed:ecommerce_event_driven.shipment.modules.shipment.infra.inbound.messaging.events.OrderConfirmedEvent,\
  orderCancelled:ecommerce_event_driven.shipment.modules.shipment.infra.inbound.messaging.events.OrderCancelledEvent
```

### 9.5 `user`

Não consome nada. Publica `user.deleted` pela outbox.

---

## 10. Sagas ponta a ponta

`═══►` evento Kafka · `───►` HTTP

### 10.1 Caminho feliz

```
POST /orders ─► ORDER  pending
  ORDER     ═══[order.created]═══════════════► INVENTORY  held (30 min)
  INVENTORY ═══[stock.reserved]══════════════► ORDER      (projeção)
POST /payments ─► PAYMENT ─► Mercado Pago … webhook ─► PAYMENT  captured
  PAYMENT   ═══[payment.approved]════════════► ORDER      pending → paid
  ORDER     ═══[order.paid]══════════════════► INVENTORY  held → confirmed, stock -= qty
  INVENTORY ═══[stock.committed]═════════════► ORDER      paid → processing
  ORDER     ═══[order.confirmed]═════════════► SHIPMENT   cria envio pending
  SHIPMENT  ═══[shipment.status.changed]═════► ORDER      (projeção)
PATCH /shipments/{id} in_transit ─► SHIPMENT
  SHIPMENT  ═══[shipment.status.changed]═════► ORDER      processing → shipped
POST /shipments/{id}/confirm-delivery ─► SHIPMENT
  SHIPMENT  ═══[shipment.status.changed]═════► ORDER      shipped → delivered
  ORDER     ═══[order.delivered]═════════════► INVENTORY  review_eligibility
```

### 10.2 Estoque recusado na reserva — `IMPLEMENTADO`

```
  ORDER     ═══[order.created]═══════════════► INVENTORY  falta estoque, nada reservado
  INVENTORY ═══[stock.rejected]══════════════► ORDER      pending → cancelled
  ORDER     ═══[order.cancelled]═════════════► INVENTORY  nada a liberar (ignorado)
```

### 10.3 PIX não pago

```
  (30 min sem payment.approved)
  job de TTL no ORDER                                     pending → cancelled
  ORDER     ═══[order.cancelled]═════════════► INVENTORY  held → released
```

O QR do PIX é criado com `date_of_expiration` igual ao TTL da reserva, para que o MP pare de
aceitar o pagamento mais ou menos quando o pedido cai.

### 10.4 PIX pago depois do cancelamento

```
  ORDER                                                   pending → cancelled (TTL)
  PAYMENT   ═══[payment.approved]════════════► ORDER      já cancelled: status não muda
  ORDER     ═══[order.refund.requested]══════► PAYMENT    estorna no MP
  PAYMENT   ═══[payment.refunded]════════════► ORDER      cancelled → refunded
```

### 10.5 Reserva venceu e o estoque acabou antes do pagamento

```
  PAYMENT   ═══[payment.approved]════════════► ORDER      pending → paid
  ORDER     ═══[order.paid]══════════════════► INVENTORY  reserva vencida, sem disponível
  INVENTORY ═══[stock.commit.failed]═════════► ORDER      paid → cancelled
  ORDER     ═══[order.cancelled]═════════════► INVENTORY  nada a liberar
  ORDER     ═══[order.refund.requested]══════► PAYMENT    estorna no MP
  PAYMENT   ═══[payment.refunded]════════════► ORDER      cancelled → refunded
```

Fecha sozinho: o cliente não recebe o produto e recebe o dinheiro de volta, sem ninguém da loja
agir. (No desenho anterior esse era o único caso que ia para revisão manual.)

### 10.6 Cliente ou loja cancela um pedido pago

```
POST /orders/{id}/cancel ─► ORDER                          processing → cancelled
  ORDER     ═══[order.cancelled]═════════════► INVENTORY  confirmed → released, stock += qty
                                             ► SHIPMENT   pending → cancelled
  ORDER     ═══[order.refund.requested]══════► PAYMENT    estorna no MP
  SHIPMENT  ═══[shipment.status.changed]═════► ORDER      eco do cancelamento (ignorado)
  PAYMENT   ═══[payment.refunded]════════════► ORDER      cancelled → refunded
```

### 10.7 Loja cancela o envio

```
POST /shipments/{id}/cancel ─► SHIPMENT                    pending → cancelled
  SHIPMENT  ═══[shipment.status.changed]═════► ORDER      processing → cancelled
  ORDER     ═══[order.cancelled]═════════════► INVENTORY  confirmed → released, stock += qty
                                             ► SHIPMENT   já cancelled (ignorado)
  ORDER     ═══[order.refund.requested]══════► PAYMENT    estorna
  PAYMENT   ═══[payment.refunded]════════════► ORDER      cancelled → refunded
```

Os dois caminhos de cancelamento (§10.6 e §10.7) terminam no mesmo estado, e nenhum deles entra
em loop: cada consumidor ignora o eco porque o seu próprio estado já está à frente.

### 10.8 Pagamento em dobro

```
  PAYMENT   ═══[payment.approved 9901]═══════► ORDER      pending → paid
  PAYMENT   ═══[payment.approved 9904]═══════► ORDER      já pago por 9901
  ORDER     ═══[order.refund.requested 9904]═► PAYMENT    estorna só o segundo
```

---

## 11. Falhas, retry e DLT

### 11.1 Retry **bloqueante**, não `@RetryableTopic`

`docs/kafka.md` §4 sugere `@RetryableTopic`, que tira a mensagem problemática da partição e a
reprocessa num tópico de retry separado. Aqui isso **quebra a ordem por pedido**: se o
`in_transit` de um envio vai para `…-retry-0`, o `delivered` do mesmo pedido passa na frente.

O volume de uma loja única não precisa da vazão que o retry não bloqueante compra. Então:
`DefaultErrorHandler` com backoff exponencial, reprocessando **na própria partição**, e
`DeadLetterPublishingRecoverer` quando as tentativas acabam.

```java
@Bean
DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
    // Mesma particao no -dlt: o reprocessamento manual preserva a ordem por pedido.
    var recoverer = new DeadLetterPublishingRecoverer(template,
            (record, ex) -> new TopicPartition(record.topic() + "-dlt", record.partition()));

    var backoff = new ExponentialBackOff(1_000, 2.0);
    backoff.setMaxInterval(10_000);
    backoff.setMaxElapsedTime(60_000);   // 1s, 2s, 4s, 8s, 10s... desiste em ~1 min

    var handler = new DefaultErrorHandler(recoverer, backoff);
    // Falha permanente: tentar de novo so atrasa a particao inteira.
    handler.addNotRetryableExceptions(
            DeserializationException.class,
            InvalidEventException.class,
            DataIntegrityViolationException.class);
    return handler;
}
```

| Exceção | Tratamento |
|---|---|
| `DeserializationException` (JSON inválido, pego pelo `ErrorHandlingDeserializer`) | DLT direto |
| `InvalidEventException` (validação do payload no consumidor) | DLT direto |
| `DataIntegrityViolationException` que **não** é a de idempotência | DLT direto — a de idempotência é tratada no código e vira `ignorado` |
| Qualquer outra (banco fora, timeout HTTP, `5xx`) | retry com backoff, depois DLT |

### 11.2 DLT

| | |
|---|---|
| **Nome** | `<tópico>-dlt` (ex.: `ecommerce.order.paid.v1-dlt`) |
| **Partições** | as mesmas do tópico original |
| **Headers** | o `DeadLetterPublishingRecoverer` anexa `kafka_dlt-exception-fqcn`, `kafka_dlt-exception-message`, `kafka_dlt-original-offset` |
| **Quem olha** | log `ERROR` + métrica `kafka.dlt.records` por tópico; mensagem na DLT é incidente |
| **Reprocessar** | depois de corrigir a causa, republicar do `-dlt` para o tópico original. Como todo consumidor é idempotente, reprocessar o que já tinha passado não causa efeito duplo |

### 11.3 O que acontece com o pedido se um evento cai na DLT

| Evento na DLT | Pedido fica | Saída |
|---|---|---|
| `order.created` | `pending` sem reserva | não trava: `order.paid` baixa o estoque sem reserva (§4.3) |
| `order.paid` | `paid` | parado. Reprocessar a DLT |
| `order.confirmed` | `processing` sem envio | parado. Em geral é endereço removido — ver §13.2, item 8 |
| `order.refund.requested` | `cancelled` sem estorno | a loja estorna por `POST /payments/{id}/refund` |
| `payment.approved` | `pending` | o job de TTL cancela; dinheiro divergente é analisado à mão |
| `shipment.status.changed` (`in_transit`) | `processing` | o `delivered` seguinte leva a `delivered` direto (§3.1) |

---

## 12. O que mudou em relação ao desenho anterior

A §11 de `docs/api-contracts.md` tinha um primeiro esboço de 12 tópicos. Ao fechar os fluxos
ponta a ponta, estas foram as correções:

| Antes | Agora | Por quê |
|---|---|---|
| `inventory` consumia `payment.approved` | consome `order.paid` | a relação entre pagamento e estoque passa pela máquina de estados do pedido, não por dois serviços reagindo ao mesmo fato em paralelo |
| `shipment` consumia `order.paid` | consome `order.confirmed` | com `order.paid`, o envio nascia **ao mesmo tempo** que a baixa de estoque, e podia existir envio de pedido sem estoque |
| `payment` consumia `order.cancelled` | consome `order.refund.requested` | três situações pedem estorno (cancelamento, pagamento tardio, pagamento em dobro); um evento só para as três |
| `inventory` consumia `payment.refunded` | não consome | dinheiro devolvido não significa mercadoria de volta. Estoque volta no `order.cancelled`; devolução física volta pela loja (`PATCH /products/{id}/stock`) |
| `user.updated` → `inventory` | removido | `review.user_name` é snapshot: o nome na avaliação é o da época. Só a remoção da conta (LGPD) altera snapshot |
| reserva vencida no pagamento ia para revisão manual | `stock.commit.failed` cancela e estorna sozinho | nenhum caso da saga depende de humano |
| — | novos: `order.confirmed`, `order.refund.requested`, `stock.committed`, `stock.commit.failed` | |
| produtor publicava com `KafkaTemplate` depois do commit | grava na `outbox` na mesma transação; o Debezium publica | [ADR-001](outbox-debezium.md): a janela de perda entre commit e `send` deixa de existir |

---

## 13. Pendências e ordem de implementação

### 13.1 Pendências no que já existe

1. **Janela de perda entre commit e `send`.** `ReserveStockService` e `CancelOrderService`
   publicam depois do commit, e `kafkaTemplate.send(...)` devolve um `CompletableFuture` que
   ninguém olha: se o broker recusar ou o processo cair entre os dois, o evento some em
   silêncio. **Decidido:** outbox + Debezium ([ADR-001](outbox-debezium.md)); resolve na fase 4.
2. **Reentrega de `order.created` não republica `stock.reserved`.** Resolvido pela mesma
   decisão: com as reservas e o evento na mesma transação, não existe reserva commitada sem
   `stock.reserved`, e não há o que republicar.
3. **Nenhum tratamento de erro configurado.** Vale o `DefaultErrorHandler` padrão do
   spring-kafka: 10 tentativas **sem intervalo** e depois só log — a mensagem é descartada.
   Implementar §11.
4. **Tópicos não declarados.** Nenhum `NewTopic` no código; os tópicos nascem por auto-criação
   do broker no primeiro `send`. Declarar no serviço **produtor** de cada tópico, com o `-dlt`
   correspondente.
5. **Validação de payload nos consumidores.** `ReserveStockService` com `items` vazio devolve
   `RESERVED` e publica `stock.reserved` para um pedido sem nada reservado. Cada consumidor
   valida o record na entrada e lança `InvalidEventException`.

### 13.2 Pendências para o que é planejado

6. **Kafka em `payment`, `shipment` e `user`.** O compose não passa
   `SPRING_KAFKA_BOOTSTRAP_SERVERS` para eles, e o `application.properties` deles não tem o
   bloco de serializer/deserializer de `docs/kafka.md` §2.1.
7. **Projeções no `order`.** O `order` precisa guardar `paymentId` (para
   `order.refund.requested`), status do pagamento, `shipmentId`, status do envio e status da
   reserva (para o `GET /orders/{id}`). Migration nova no `order` (a `V4` é a da outbox).
8. **Endereço removido antes da criação do envio.** `order.confirmed` busca o endereço em
   `GET /internal/addresses/{id}`, que devolve `404` para endereço removido. Essa rota precisa
   enxergar endereço com `deleted_at` — o pedido aponta para ele, e remover da lista do cliente
   não pode impedir a entrega. Resolve junto com a pendência de endereço imutável de
   `docs/api-contracts.md` §13.2.
9. **Cancelamento além de `pending`.** `OrderCancellationTransaction.cancelIfPending` só cancela
   `pending`. `paid` e `processing` precisam da transição e da publicação de
   `order.refund.requested`.
10. **Estoque de reserva `confirmed` volta no cancelamento.** Hoje
    `releaseHeldByOrder` só mexe em `held` — de propósito, e o comentário no código explica.
    Com cancelamento de pedido pago, `confirmed` → `released` com `stock += quantity` passa a
    ser necessário, numa operação separada e explícita.
11. **Job de TTL do checkout** no `order`: cancela `pending` com mais de 30 min.
12. **Estorno idempotente no MP**: `X-Idempotency-Key: refund-{paymentId}` (§4.6).

### 13.3 Ordem sugerida

| # | Entrega | Destrava |
|---|---|---|
| 1 | Tratamento de erro + DLT (§11) e `NewTopic` nos produtores | qualquer evento novo nasce já com a rede de segurança |
| 2 | Outbox + Debezium, fases 1–4 de `docs/outbox-debezium.md` | fecha a perda silenciosa do que já existe; todo evento novo já nasce com garantia de entrega |
| 3 | Projeções no `order` (`V5`) | `paymentId` para estorno, `GET /orders/{id}` |
| 4 | `payment.approved` → `order.paid` → `stock.committed` / `commit.failed` | pagamento baixa estoque |
| 5 | `order.confirmed` → envio; `shipment.status.changed` → pedido | entrega |
| 6 | Cancelamento de pedido pago + `order.refund.requested` + `payment.refunded` | dinheiro volta sozinho |
| 7 | `order.delivered` → elegibilidade de avaliação | pós-venda |
| 8 | `user.deleted` | LGPD |

---

**Fim do documento.**
