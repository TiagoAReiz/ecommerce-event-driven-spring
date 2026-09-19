# Spec Delta

## Purpose

Baixa de estoque transacional e devolução de estoque controlam o fluxo de inventário após pagamento aprovado e cancelamento de pedido, garantindo consistência atômica e tratamento especial para reservas vencidas.

## ADDED Requirements

### Requirement: Baixar estoque de forma atômica ao pagamento
O sistema SHALL, ao receber evento `order.paid`, confirmar reservas de estoque e debitar a quantidade do `product.stock` de forma atômica (tudo-ou-nada), mesmo se a reserva original tiver expirado, e publicar sucesso ou falha.

#### Scenario: Baixa bem-sucedida com reserva válida
- **WHEN** evento `order.paid` chega com 2 itens que têm reserva `held` válida dentro de 30 minutos
- **THEN** numa transação única: muda-se `held` → `confirmed`, debita-se `product.stock` pela quantidade, publica-se `stock.committed`, e retorna processado

#### Scenario: Baixa bem-sucedida com reserva expirada mas estoque disponível
- **WHEN** evento `order.paid` chega e a reserva venceu (mais de 30 min), mas ainda há estoque disponível
- **THEN** confirma-se a reserva mesmo vencida, debita-se o estoque normalmente, publica-se `stock.committed`

#### Scenario: Baixa bem-sucedida sem reserva prévia e estoque disponível
- **WHEN** evento `order.paid` chega e não há reserva no banco (ex.: se `order.created` foi para DLT), mas há estoque disponível
- **THEN** cria-se linha de reserva já como `confirmed`, debita-se o estoque, publica-se `stock.committed`

#### Scenario: Falha por estoque insuficiente
- **WHEN** evento `order.paid` chega mas falta estoque para algum item (reserva `released` ou disponível insuficiente)
- **THEN** faz-se rollback de todas as mudanças, libera-se qualquer reserva `held` do pedido, publica-se `stock.commit.failed` com reason `EXPIRED_WITHOUT_STOCK` ou `RESERVATION_RELEASED`, retorna recusado

#### Scenario: Falha por reserva já confirmada (reentrega)
- **WHEN** evento `order.paid` chega e a reserva já está `confirmed`
- **THEN** não se baixa de novo o estoque, ignora-se o evento (já foi processado), e NÃO se publica `stock.committed` novamente

### Requirement: Usar ordem de ids para evitar deadlock
O sistema SHALL processar itens do pedido em ordem crescente de `product_id` ao travancar linhas de produto na transação de baixa.

#### Scenario: Processamento em ordem
- **WHEN** evento `order.paid` chega com produtos ids 118, 42, 95
- **THEN** as linhas de `product` são travadas e verificadas na ordem 42, 95, 118 (ascendente) para evitar deadlock com outros pedidos que usam os mesmos produtos

### Requirement: Devolver estoque confirmado no cancelamento
O sistema SHALL, ao receber evento `order.cancelled` com um pedido que tem reserva `confirmed`, devolver o estoque (incrementar `product.stock` pela quantidade), mudar o status para `released`, e fazer tudo numa operação separada e explícita.

#### Scenario: Devolução de estoque confirmado
- **WHEN** evento `order.cancelled` chega para pedido com reserva `confirmed`
- **THEN** numa transação: muda-se `confirmed` → `released`, incrementa-se `product.stock` pela quantidade, e retorna processado

#### Scenario: Cancelamento de reserva `held` apenas libera
- **WHEN** evento `order.cancelled` chega para pedido com apenas reserva `held`
- **THEN** muda-se `held` → `released` sem mexer em `product.stock` (estoque nunca foi debitado), retorna processado

#### Scenario: Cancelamento sem reserva
- **WHEN** evento `order.cancelled` chega para pedido sem linha de reserva
- **THEN** ignora-se o evento, retorna ignorado

#### Scenario: Cancelamento com reserva já liberada
- **WHEN** evento `order.cancelled` chega para pedido com reserva já `released`
- **THEN** ignora-se o evento, retorna ignorado

### Requirement: Evitar devolução dupla no cancelamento
O sistema SHALL garantir que uma reentrega do evento `order.cancelled` não devolva o estoque duas vezes, checando o estado atual da reserva antes de devolver.

#### Scenario: Reentrega de cancelamento idempotente
- **WHEN** evento `order.cancelled` é reenviado para o mesmo pedido
- **THEN** a segunda vez encontra a reserva já em estado `released` e ignora o evento (idempotente)

### Requirement: Publicar evento de sucesso ou falha atomicamente
O sistema SHALL publicar `stock.committed` ou `stock.commit.failed` na mesma transação da mudança de estado da reserva e estoque, garantindo que nenhum evento sai sem que o estado foi commitado.

#### Scenario: Publicação de sucesso na mesma transação
- **WHEN** baixa bem-sucedida
- **THEN** evento `stock.committed` é gravado na tabela `outbox` na mesma transação da baixa, e o Debezium o publica

#### Scenario: Publicação de falha na transação de rollback
- **WHEN** falta estoque
- **THEN** rollback da transação de baixa, nova transação apenas para liberar `held` e publicar `stock.commit.failed` na tabela `outbox`

### Requirement: Tratar razões de falha adequadamente
O sistema SHALL indicar corretamente a razão da falha de comprometimento de estoque no evento `stock.commit.failed`.

#### Scenario: Razão EXPIRED_WITHOUT_STOCK
- **WHEN** reserva venceu e não há mais estoque disponível
- **THEN** publica-se `stock.commit.failed` com `reason = "EXPIRED_WITHOUT_STOCK"`

#### Scenario: Razão RESERVATION_RELEASED
- **WHEN** reserva havia sido liberada antes do pagamento (pedido cancelado na janela de pagamento)
- **THEN** publica-se `stock.commit.failed` com `reason = "RESERVATION_RELEASED"`

### Requirement: Segurança transacional na confirmação de estoque
O sistema SHALL usar locking pessimista em nível de linha de produto (`FOR UPDATE` ou equivalente) para evitar race condition onde dois pagamentos do mesmo pedido tentam confirmar estoque simultaneamente.

#### Scenario: Locking de produtos no início da transação
- **WHEN** transação de `CommitStockService` inicia
- **THEN** todas as linhas de `product` dos itens são imediatamente travadas em ordem de id para garantir consistência até o final da transação

#### Scenario: Sem deadlock por ordem
- **WHEN** dois pagamentos em paralelo tentam baixar estoque de dois produtos diferentes
- **THEN** ambos travam na mesma ordem (ascendente) e nenhum fica esperando circularmente pelo outro
