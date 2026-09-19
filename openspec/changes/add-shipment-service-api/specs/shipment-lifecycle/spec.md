# Spec Delta

## Purpose

Define o ciclo de vida do envio desde a criação até a entrega ou cancelamento, com transições de status controladas por quem pode agir (loja ou comprador) e publicação de eventos em cada mudança.

## ADDED Requirements

### Requirement: Criar envio idempotente ao receber order.confirmed

Quando o sistema consome o evento `order.confirmed`, MUST criar um envio no status `pending` se ele ainda não existe para o pedido. A criação MUST ser idempotente: consumir o mesmo evento várias vezes (reentrega) não cria duplicatas.

O sistema MUST buscar o endereço de destino em `GET /internal/addresses/{addressId}?userId={customerId}` (com token de serviço `internal:hydrate`), copiar para os campos `to_*` do envio, usar a origem configurada em `app.store.origin.*` para os campos `from_*`, e copiar o `freightCost` do evento para o campo `freight_tax` do envio.

#### Scenario: Envio criado com sucesso ao chegar order.confirmed

- **WHEN** o evento `order.confirmed` chega com `{orderId: 3301, customerId: 42, addressId: 15, freightCost: 25.00}`
- **THEN** um envio é criado com `idOrder: 3301, idUser: 42, status: "pending", freightTax: "25.00", to_city: "...", to_state: "SP", to_zipcode: "01310100", from_city: "Guarulhos", from_state: "SP"`
- **THEN** o evento `shipment.status.changed` é publicado com `from: null, to: "pending"`

#### Scenario: Reentrega de order.confirmed não duplica envio

- **WHEN** o mesmo evento `order.confirmed` é consumido duas vezes
- **THEN** apenas um envio existe para o pedido; o segundo consumo é ignorado (idempotência via `shipment_order_uk`)

#### Scenario: Endereço do comprador removido após checkout

- **WHEN** o endereço foi removido (soft delete) entre o checkout e a confirmação do pedido
- **THEN** a chamada a `GET /internal/addresses/{id}?userId={customerId}` retorna `404`
- **THEN** o evento vai para a DLT (nenhum envio é criado; o pedido vai para `cancelled`)

### Requirement: Transições de status autorizadas

O sistema MUST implementar as transições de status conforme a tabela:

| De | Para | Quem | Autorização |
|---|---|---|---|
| null | `pending` | sistema, ao receber `order.confirmed` | — |
| `pending` | `ready_to_ship` | loja | papel `owner` + escopo `shipments:write` |
| `ready_to_ship` | `in_transit` | loja | papel `owner` + escopo `shipments:write` |
| `in_transit` | `out_for_delivery` | loja | papel `owner` + escopo `shipments:write` (opcional) |
| `in_transit` ou `out_for_delivery` | `delivered` | comprador (automático via evento `shipment.status.changed` → `delivered`) | escopo `shipments:write` + autenticação com `sub == shipment.id_user` |
| `in_transit` ou `out_for_delivery` | `delivered` | sistema (job de 15 dias) | — |
| `in_transit` | `returned` | loja | papel `owner` + escopo `shipments:write` |
| `pending` ou `ready_to_ship` | `cancelled` | loja (via `POST /shipments/{id}/cancel`) | papel `owner` + escopo `shipments:write` |
| `pending` ou `ready_to_ship` | `cancelled` | sistema (ao consumir `order.cancelled`) | — |

Qualquer tentativa de transição não autorizada MUST retornar `403 FORBIDDEN`. Qualquer tentativa de transição inválida MUST retornar `409 CONFLICT`.

#### Scenario: Loja avança envio de pending para ready_to_ship

- **WHEN** um usuário com papel `owner` e token `shipments:write` faz `PATCH /shipments/{id}` com `{status: "ready_to_ship"}`
- **THEN** o envio transita para `ready_to_ship`
- **THEN** o evento `shipment.status.changed` é publicado com `from: "pending", to: "ready_to_ship"`

#### Scenario: Comprador tenta marcar como entregue antes de despacho

- **WHEN** um envio está em `pending` ou `ready_to_ship` e o comprador faz `POST /shipments/{id}/confirm-delivery`
- **THEN** a resposta é `409 CONFLICT` (transição inválida; o envio ainda não saiu)

#### Scenario: Não-dono tenta despachar

- **WHEN** um usuário sem papel `owner` faz `PATCH /shipments/{id}` com status
- **THEN** a resposta é `403 FORBIDDEN`

### Requirement: Publicar evento shipment.status.changed em toda transição

Toda mudança de status de um envio MUST publicar o evento `shipment.status.changed` na outbox de forma transacional (junto com a mudança de status no banco).

O evento MUST incluir: `shipmentId`, `orderId` (como key), `from` (null na criação), `to`, `trackingCode` (se fornecido), `reason` (só em cancelamento), `changedAt`.

#### Scenario: Evento publicado ao criar envio

- **WHEN** um envio é criado no status `pending`
- **THEN** um evento com `{shipmentId: 5501, orderId: 3301, from: null, to: "pending", changedAt: "2026-09-19T...Z"}` é gravado na outbox

#### Scenario: Evento publicado ao avançar para in_transit

- **WHEN** a loja faz `PATCH /shipments/{id}` com `{status: "in_transit", trackingCode: "AA123456789BR"}`
- **THEN** um evento com `{from: "ready_to_ship", to: "in_transit", trackingCode: "AA123456789BR", changedAt: "..."}` é publicado

#### Scenario: Evento publicado ao cancelar com motivo

- **WHEN** a loja faz `POST /shipments/{id}/cancel` com `{reason: "Produto danificado"}`
- **THEN** um evento com `{from: "pending", to: "cancelled", reason: "Produto danificado", changedAt: "..."}` é publicado

### Requirement: Comprador confirma entrega (idempotente)

Quando o comprador faz `POST /shipments/{id}/confirm-delivery`, o envio MUST transitar para `delivered` se estiver em `in_transit` ou `out_for_delivery`. A operação MUST ser idempotente: se o envio já está `delivered`, a resposta é `200` e nenhuma ação adicional ocorre.

O comprador é identificado pelo claim `sub` do token; deve igualar `shipment.id_user` ou a resposta é `404` (ocultar a existência do envio).

#### Scenario: Comprador confirma entrega com sucesso

- **WHEN** um envio está em `in_transit` e o comprador (com token `shipments:write`, `sub == 42`) faz `POST /shipments/{id}/confirm-delivery`
- **THEN** o envio transita para `delivered`
- **THEN** o evento `shipment.status.changed` é publicado com `to: "delivered"`
- **THEN** a resposta é `200`

#### Scenario: Confirmação de entrega já realizada (idempotência)

- **WHEN** um envio já está em `delivered` e o comprador faz `POST /shipments/{id}/confirm-delivery` novamente
- **THEN** a resposta é `200` (operação bem-sucedida)
- **THEN** nenhum novo evento é publicado

#### Scenario: Comprador de outro pedido tenta confirmar entrega

- **WHEN** um usuário com `sub == 99` tenta acessar `POST /shipments/{id}` de um envio onde `id_user == 42`
- **THEN** a resposta é `404` (não confirmar existência)

### Requirement: Loja cancela envio com motivo

Quando a loja faz `POST /shipments/{id}/cancel` com um motivo (até 500 caracteres), o envio MUST transitar para `cancelled` se estiver em `pending` ou `ready_to_ship`. Se o envio estiver em `in_transit` ou além, a transição NÃO é permitida (o caminho é `returned`).

#### Scenario: Loja cancela envio pendente

- **WHEN** um envio está em `pending` e a loja (papel `owner`, `shipments:write`) faz `POST /shipments/{id}/cancel` com `{reason: "Produto danificado no armazém"}`
- **THEN** o envio transita para `cancelled`
- **THEN** o campo `cancel_reason` é preenchido com o motivo
- **THEN** o evento `shipment.status.changed` é publicado com `to: "cancelled", reason: "Produto danificado no armazém"`
- **THEN** a resposta é `200`

#### Scenario: Loja tenta cancelar envio já em trânsito

- **WHEN** um envio está em `in_transit` e a loja tenta `POST /shipments/{id}/cancel`
- **THEN** a resposta é `409 CONFLICT` (transição inválida; cancele pelo status `returned`)

#### Scenario: Motivo muito longo

- **WHEN** a loja tenta cancelar com `{reason: "..."}` (string acima de 500 caracteres)
- **THEN** a resposta é `400 BAD_REQUEST`

### Requirement: Consumidor order.cancelled cancela envios pendentes

Quando o sistema consome o evento `order.cancelled`, MUST cancelar qualquer envio do pedido que esteja em `pending` ou `ready_to_ship`. Envios já em `in_transit` ou além são ignorados (não podem ser cancelados por evento).

#### Scenario: Envio cancelado junto com o pedido

- **WHEN** um pedido é cancelado e seu envio está em `ready_to_ship`
- **THEN** o envio transita para `cancelled` (sem motivo)
- **THEN** o evento `shipment.status.changed` é publicado (consumidor de `order` reconhece como eco e ignora)

#### Scenario: Envio em trânsito não é tocado

- **WHEN** um pedido é cancelado mas seu envio já está em `in_transit`
- **THEN** o envio não muda (fica `in_transit`)
- **THEN** um log `WARN` é registrado (mercadoria já saiu, é devolução)

### Requirement: Confirmação automática após 15 dias

Um job diário MUST executar à 04:00 (UTC) e procurar envios em `in_transit` ou `out_for_delivery` cuja última atualização foi há mais de 15 dias. Para cada um, MUST transitar para `delivered` e publicar o evento correspondente.

#### Scenario: Job auto-confirma entrega após 15 dias

- **WHEN** um envio está em `in_transit` desde `2026-09-04T08:00:00Z` (mais de 15 dias atrás)
- **WHEN** o job executa em `2026-09-20T04:00:00Z`
- **THEN** o envio transita para `delivered`
- **THEN** o evento `shipment.status.changed` é publicado com `to: "delivered"`

### Requirement: Consultas de envios com autorização

Leituras de envios (`GET /shipments`, `GET /shipments/{id}`, `GET /shipments/manage`) MUST retornar `404` para envios que não pertencem a quem consulta (a menos que seja o `owner`).

`GET /shipments` (do comprador) MUST listar seus próprios envios. `GET /shipments/manage` (do `owner`) MUST listar todos os envios da loja.

#### Scenario: Comprador vê seus próprios envios

- **WHEN** um comprador com `sub == 42` faz `GET /shipments`
- **THEN** a resposta lista apenas envios onde `id_user == 42`

#### Scenario: Comprador tenta ver envio de outro

- **WHEN** um comprador com `sub == 42` faz `GET /shipments/{id}` de um envio onde `id_user == 99`
- **THEN** a resposta é `404`

#### Scenario: Dono vê todos os envios em /manage

- **WHEN** a loja (papel `owner`, `sales:read`) faz `GET /shipments/manage`
- **THEN** a resposta lista todos os envios de todos os pedidos, filtráveis por `status`, `orderId`

### Requirement: Rastreamento opcional

Um envio PODE ter um `trackingCode` (até 60 caracteres de texto livre) fornecido pela loja ao avançar o status. Este campo MUST ser `null` até o primeiro despacho.

O campo NÃO MUST ser gravado ou atualizado em nenhuma outra transição além de `PATCH /shipments/{id}` (quando a loja muda status).

#### Scenario: Loja fornece código de rastreamento

- **WHEN** a loja faz `PATCH /shipments/{id}` com `{status: "in_transit", trackingCode: "AA123456789BR"}`
- **THEN** o `trackingCode` é salvo no envio
- **THEN** ele aparece em `GET /shipments/{id}` e em `GET /shipments`

#### Scenario: Rastreamento não pode exceder 60 caracteres

- **WHEN** a loja tenta `PATCH /shipments/{id}` com `{trackingCode: "..."}` (acima de 60 caracteres)
- **THEN** a resposta é `400 BAD_REQUEST`

### Requirement: Criar envio via POST /internal/shipments (contingência)

O sistema MUST aceitar uma rota interna `POST /internal/shipments` para criar envios fora do fluxo normal (contingência). Esta rota MUST exigir `internal:hydrate` e aceitar `{idOrder, idUser, idAddressUser, freightTax}`.

#### Scenario: Criação por contingência

- **WHEN** um serviço com token `internal:hydrate` faz `POST /internal/shipments` com `{idOrder: 3301, idUser: 42, idAddressUser: 15, freightTax: "25.00"}`
- **THEN** o envio é criado como no fluxo normal
- **THEN** a resposta é `201 Created`

#### Scenario: Endereço não encontrado na rota interna

- **WHEN** `GET /internal/addresses/{idAddressUser}?userId={idUser}` retorna `404`
- **THEN** a resposta é `404`

### Requirement: Códigos de erro e autorização

As rotas retornam códigos padrão: `400` (requisição inválida), `401` (token ausente/inválido), `403` (escopo/papel insuficiente), `404` (recurso inexistente ou não pertence a quem chama), `409` (transição inválida), `422` (estado inválido para operação), `500`/`503` (falha de banco).

Tentar executar `delivered` via `PATCH /shipments/{id}` (rota de loja) MUST retornar `403` (reservado ao comprador).

#### Scenario: Loja tenta marcar delivered via PATCH

- **WHEN** a loja faz `PATCH /shipments/{id}` com `{status: "delivered"}`
- **THEN** a resposta é `403 FORBIDDEN` (operação reservada ao comprador)
