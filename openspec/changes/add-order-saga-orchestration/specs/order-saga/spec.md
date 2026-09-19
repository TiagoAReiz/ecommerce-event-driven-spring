# Spec Delta

## Purpose

Orquestra o ciclo de vida do pedido respondendo a eventos de estoque, pagamento, envio e remoção de conta, garantindo que transições de estado sejam atômicas e que casos especiais (pagamento fora de tempo, valor divergente, duplo pagamento) sejam tratados sem perder consistência.

## ADDED Requirements

### Requirement: Pedido consome `stock.reserved` e atualiza projeção de reserva

O sistema SHALL reagir ao evento `stock.reserved` atualizando a projeção do pedido quando ele estiver em estado `pending`, refletindo que o estoque foi reservado com sucesso.

#### Scenario: Reserva confirmada enquanto pedido aguarda pagamento
- **WHEN** o pedido está em `pending` e recebe `stock.reserved`
- **THEN** a projeção `stock_reservation` é definida como `reserved` (IMPLEMENTADO) ou exibida no `GET /orders/{id}`

#### Scenario: Evento de reserva chega para pedido que não está mais em pending
- **WHEN** o pedido não está em estado `pending` (cancelado, pago, etc.)
- **THEN** o evento é ignorado e nenhuma projeção é atualizada

#### Scenario: Payload inválido no evento de reserva
- **WHEN** o evento `stock.reserved` tem JSON inválido ou campos obrigatórios faltando
- **THEN** uma `InvalidEventException` é lançada e o evento vai para a DLT

### Requirement: Pedido consome `stock.committed` e transiciona para `processing`

O sistema SHALL reagir ao evento `stock.committed` movendo o pedido de `paid` para `processing` e publicando `order.confirmed` pela outbox dentro da mesma transação.

#### Scenario: Estoque foi baixado com sucesso após pagamento
- **WHEN** o pedido está em `paid` e recebe `stock.committed`
- **THEN** o status transiciona para `processing`, publica `order.confirmed` com os campos obrigatórios

#### Scenario: Evento de commit chega para pedido que já está em processing ou além
- **WHEN** o pedido está em `processing`, `shipped`, `delivered` ou `refunded` (reentrega)
- **THEN** o evento é ignorado e nada é publicado (a transição e publicação já ocorreram na primeira entrega)

#### Scenario: Evento de commit chega para pedido cancelado
- **WHEN** o pedido foi cancelado enquanto a baixa de estoque acontecia
- **THEN** o evento é ignorado; o pedido já saiu de `paid` e o `order.cancelled` trata o estorno

#### Scenario: Erro na atualização de estado para processing
- **WHEN** a transação de atualização falha (banco indisponível, lock)
- **THEN** a exceção é retentável (não é `InvalidEventException`) e o evento é reprocessado com backoff

### Requirement: Pedido consome `stock.commit.failed` e cancela com estorno automático

O sistema SHALL reagir ao evento `stock.commit.failed` (estoque indisponível após pagamento) movendo o pedido de `paid` para `cancelled` e publicando `order.cancelled` e `order.refund.requested` na mesma transação.

#### Scenario: Reserva venceu e não há disponível após pagamento aprovado
- **WHEN** o pedido está em `paid` e recebe `stock.commit.failed` com `reason = EXPIRED_WITHOUT_STOCK`
- **THEN** status vai para `cancelled`, publica `order.cancelled` com motivo do produto e `order.refund.requested` com `paymentId`

#### Scenario: Reserva foi já liberada (cancelada enquanto pagamento acontecia)
- **WHEN** o pedido está em `paid` e recebe `stock.commit.failed` com `reason = RESERVATION_RELEASED`
- **THEN** status vai para `cancelled`, publica `order.cancelled` e `order.refund.requested`

#### Scenario: Evento de commit failed chega para pedido que já está cancelado
- **WHEN** o pedido já transitou para `cancelled` (por outro caminho)
- **THEN** o evento é ignorado; o cancelamento já solicitou o estorno

#### Scenario: Payload inválido ou pedido inexistente
- **WHEN** o evento tem JSON inválido ou referencia um `orderId` que não existe
- **THEN** uma `InvalidEventException` é lançada e o evento vai para a DLT

### Requirement: Pedido consome `payment.approved` e transiciona para `paid`

O sistema SHALL reagir ao evento `payment.approved` validando o valor, o estado do pedido e registrando o pagamento, movendo o pedido de `pending` para `paid` e publicando `order.paid` quando as condições forem atendidas.

#### Scenario: Pagamento aprovado com valor correto enquanto pedido aguarda em pending
- **WHEN** o pedido está em `pending`, o `amount` do evento iguala `total_cost` do pedido e é o primeiro pagamento
- **THEN** status transiciona para `paid`, campos `payment_id` e `payment_status = captured` são gravados, publica `order.paid`

#### Scenario: Pagamento aprovado com valor divergente (vai para DLT)
- **WHEN** o `amount` do evento diferencia `total_cost` do pedido
- **THEN** uma `InvalidEventException` é lançada; o evento vai para a DLT; o pedido permanece inalterado

#### Scenario: Segundo pagamento chega para pedido já pago por outro paymentId
- **WHEN** o pedido já está `paid` com um `payment_id` diferente do evento
- **THEN** status não muda, publica `order.refund.requested` com o `paymentId` do segundo evento

#### Scenario: Mesmo pagamento é entregue duas vezes (reentrega ou retry)
- **WHEN** o pedido já está `paid` com o mesmo `payment_id` do evento
- **THEN** nada é publicado; o evento é ignorado por idempotência

#### Scenario: Pagamento aprovado chega para pedido já cancelado
- **WHEN** o pedido foi cancelado (TTL de PIX, stock.rejected, etc.) e depois recebe `payment.approved`
- **THEN** status permanece `cancelled`, publica `order.refund.requested` com o `paymentId` do evento

#### Scenario: Erro de transação ao gravar o pagamento
- **WHEN** a atualização do pedido falha (banco indisponível, deadlock)
- **THEN** a exceção é retentável e o evento é reprocessado

### Requirement: Pedido consome `payment.failed` e atualiza projeção de status de pagamento

O sistema SHALL reagir ao evento `payment.failed` registrando a falha na projeção sem mudar o status do pedido, permitindo que o cliente tente outro pagamento dentro da janela de reserva.

#### Scenario: Pagamento falhou enquanto pedido aguarda em pending
- **WHEN** o pedido está em `pending` e recebe `payment.failed`
- **THEN** a projeção `payment_status` é atualizada com o novo status, mas o `order_status` permanece `pending`

#### Scenario: Evento de falha de pagamento chega para pedido que já foi pago
- **WHEN** o pedido está em `paid` ou além e recebe `payment.failed`
- **THEN** a projeção não regride; o evento é ignorado

#### Scenario: Erro na atualização da projeção
- **WHEN** a transação falha ao atualizar `payment_status`
- **THEN** a exceção é retentável e o evento é reprocessado

### Requirement: Pedido consome `payment.refunded` e transiciona para `refunded` ou move via `cancelled`

O sistema SHALL reagir ao evento `payment.refunded` movendo o pedido para `refunded` se estiver em estado terminal (`cancelled`, `shipped`, `delivered`), ou transicionando por `cancelled` se estiver em `paid` ou `processing`, e registrando o valor total estornado na projeção.

#### Scenario: Estorno total de pedido já cancelado ou entregue
- **WHEN** `full: true` e o pedido está em `cancelled`, `shipped` ou `delivered`
- **THEN** status transiciona para `refunded`, `refunded_amount = totalRefunded` é gravado

#### Scenario: Estorno total de pedido pago ou em processamento (cancelamento manual ou chargeback)
- **WHEN** `full: true` e o pedido está em `paid` ou `processing`
- **THEN** status vai `paid/processing` → `cancelled` (publica `order.cancelled`), depois `cancelled` → `refunded`, `refunded_amount = totalRefunded` é gravado

#### Scenario: Estorno parcial de qualquer estado
- **WHEN** `full: false` (estorno parcial)
- **THEN** apenas a projeção `refunded_amount` é incrementada; status do pedido não muda

#### Scenario: Estorno chega para pedido que já está refunded
- **WHEN** o pedido está em `refunded`
- **THEN** o evento é ignorado; nenhuma transição ocorre

#### Scenario: Payload inválido ou pedido inexistente
- **WHEN** o evento tem JSON inválido ou referencia um `orderId` que não existe
- **THEN** uma `InvalidEventException` é lançada e o evento vai para a DLT

### Requirement: Pedido consome `shipment.status.changed` com transições de envio

O sistema SHALL reagir ao evento `shipment.status.changed` atualizando projeções de envio e transicionando o status do pedido conforme o status de destino do envio, de acordo com a máquina de estados definida.

#### Scenario: Envio criado em pending (primeira transição do event)
- **WHEN** `from: null`, `to: pending`
- **THEN** a projeção `shipment_id` é gravada no pedido

#### Scenario: Envio sai para in_transit
- **WHEN** `to: in_transit` e o pedido está em `processing`
- **THEN** status do pedido transiciona para `shipped`, `tracking_code` é gravado se presente

#### Scenario: Envio é entregue
- **WHEN** `to: delivered` e o pedido está em `processing` ou `shipped`
- **THEN** status do pedido transiciona para `delivered`, `deliveredAt = changedAt` do evento, publica `order.delivered` com os itens distintos

#### Scenario: Envio é cancelado enquanto pedido está em processamento
- **WHEN** `to: cancelled` e o pedido está em `processing`
- **THEN** status do pedido vai para `cancelled`, publica `order.cancelled` e `order.refund.requested(payment_id)`

#### Scenario: Cancelamento de envio é eco do cancelamento do próprio pedido
- **WHEN** `to: cancelled` e o pedido já está em `cancelled`
- **THEN** o evento é ignorado; é o eco do `order.cancelled` que o próprio pedido publicou

#### Scenario: Transição que faria pedido voltar (proibido por monotonicidade)
- **WHEN** qualquer estado que violaria a regra da máquina (ex.: `shipped` → `processing`)
- **THEN** o evento é ignorado com log DEBUG; nenhuma transição ocorre

#### Scenario: Outras transições de envio (ready_to_ship, out_for_delivery, returned)
- **WHEN** `to: ready_to_ship` | `out_for_delivery` | `returned`
- **THEN** apenas a projeção é atualizada; status do pedido não muda

#### Scenario: Payload inválido ou `to` fora do enum
- **WHEN** o evento tem JSON inválido ou `to` não é um valor válido de `shipment_status`
- **THEN** uma `InvalidEventException` é lançada e o evento vai para a DLT

### Requirement: Pedido consome `user.deleted` e remove carrinho

O sistema SHALL reagir ao evento `user.deleted` executando soft delete do carrinho e itens do carrinho do usuário no módulo `cart`.

#### Scenario: Usuário com carrinho é deletado
- **WHEN** o evento `user.deleted` chega e o usuário tem linhas em `cart` ou `cart_items`
- **THEN** soft delete é aplicado em `cart` e `cart_items` do `userId` do evento

#### Scenario: Usuário sem carrinho é deletado
- **WHEN** o evento `user.deleted` chega e o usuário não tem carrinho
- **THEN** nada é feito; o evento é ignorado

#### Scenario: Payload inválido
- **WHEN** o evento tem JSON inválido ou `userId` faltando
- **THEN** uma `InvalidEventException` é lançada e o evento vai para a DLT

### Requirement: Status do pedido é monotônico e não retrocede

O sistema SHALL garantir que o status do pedido nunca transicione para trás; eventos que causariam retrocesso são ignorados sem erro.

#### Scenario: Evento chega fora de ordem e faria transição para trás
- **WHEN** a máquina de estados recebe uma transição que violaria a regra (ex.: `processing` para `paid`)
- **THEN** a transição é recusada, o pedido mantém seu status atual, log DEBUG é gerado

#### Scenario: Evento de in_transit chega para pedido já em delivered
- **WHEN** o pedido já está em `delivered` e recebe `shipment.status.changed` → `in_transit`
- **THEN** o evento é ignorado; o estado atual permanece `delivered`

#### Scenario: Eventos concorrentes de stock e payment decidem pelo estado atual
- **WHEN** `stock.committed` e `stock.commit.failed` chegam fora de ordem
- **THEN** cada um lê o estado atual do pedido antes de decidir e aplica a regra correspondente sem conflito

#### Scenario: Transição permitida mantém monotonia
- **WHEN** uma transição válida ocorre (ex.: `processing` → `shipped` → `delivered`)
- **THEN** cada passo avança sem retrocesso

