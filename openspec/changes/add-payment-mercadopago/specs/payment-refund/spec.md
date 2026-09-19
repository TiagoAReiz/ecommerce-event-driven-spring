# Spec Delta

## Purpose

Processar estornos de pagamento de forma idempotente, tanto manual (pela loja) quanto automático (por evento de pedido cancelado).

## ADDED Requirements

### Requirement: Estorno manual pela loja

A API SHALL expor POST /payments/{id}/refund com escopo payments:refund (apenas owner) para estornar manualmente pagamentos totais ou parciais. O sistema MUST validar a chave de idempotência (Idempotency-Key obrigatório), o saldo estornável e a janela de 180 dias do Mercado Pago.

#### Scenario: Estorno total de pagamento capturado
- **WHEN** owner envia POST /payments/{id}/refund com Idempotency-Key e amount omitido (total)
- **THEN** sistema retorna 200, estorna no MP e atualiza status local para refunded, gravando payment.refunded

#### Scenario: Estorno parcial de pagamento
- **WHEN** owner envia POST /payments/{id}/refund com Idempotency-Key e amount menor que saldo
- **THEN** sistema retorna 200, estorna parcialmente no MP, atualiza refunded_amount mas não muda status

#### Scenario: Reenvio idempotente do estorno
- **WHEN** owner reenvia POST /payments/{id}/refund com mesmo Idempotency-Key
- **THEN** sistema retorna 200 com resultado anterior (idempotente no Redis e no MP)

#### Scenario: Estorno sem escopo payments:refund
- **WHEN** cliente sem permissions:refund tenta POST /payments/{id}/refund
- **THEN** sistema retorna 403

#### Scenario: Estorno já completo retorna 409
- **WHEN** owner tenta estornar pagamento já totalmente refunded
- **THEN** sistema retorna 409 com code=PAYMENT_ALREADY_REFUNDED

#### Scenario: Fora da janela de 180 dias
- **WHEN** owner tenta estornar pagamento anterior a 180 dias
- **THEN** sistema retorna 422 com code=REFUND_OUTSIDE_WINDOW

#### Scenario: Amount acima do saldo estornável
- **WHEN** owner envia POST /payments/{id}/refund com amount maior que saldo
- **THEN** sistema retorna 409 com code=INSUFFICIENT_BALANCE_FOR_REFUND

### Requirement: Estorno automático por order.refund.requested

A API SHALL consumir evento order.refund.requested (via Kafka consumer OrderRefundRequestedConsumer → RefundPaymentService) e estornar automaticamente o pagamento especificado no evento usando X-Idempotency-Key: refund-{paymentId} para garantir idempotência no provedor, mesmo em reentrega.

#### Scenario: Evento order.refund.requested estorna pagamento capturado
- **WHEN** order publica order.refund.requested com paymentId de pagamento captured
- **THEN** payment consome, estorna no MP com chave determinística, grava refunded_amount, atualiza status e publica payment.refunded

#### Scenario: Estorno automático ignora pagamento já refunded
- **WHEN** order.refund.requested chega para pagamento já refunded
- **THEN** payment consome, loga WARN e não tenta estornar novamente

#### Scenario: Estorno automático não processa pagamento não capturado
- **WHEN** order.refund.requested chega para pagamento com status=pending ou failed
- **THEN** payment consome, loga WARN "sem dinheiro a devolver" e não publica nada

#### Scenario: Reentrega do evento usa mesma chave
- **WHEN** order.refund.requested é reentregue (replicação Kafka)
- **THEN** payment envia chave determinística refund-{paymentId}, MP identifica e não estorna duas vezes

### Requirement: Idempotência persistida no banco

A API SHALL gravar payment.idempotency_key em estornos manuais (UNIQUE em conjunto com paymentId) e usar chave determinística refund-{paymentId} em estornos automáticos, repassando ambas ao Mercado Pago no header X-Idempotency-Key para garantir que nenhum estorno é duplicado.

#### Scenario: Chave determinística em estorno automático
- **WHEN** order.refund.requested publica estorno automático
- **THEN** payment envia X-Idempotency-Key: refund-{paymentId} ao MP, garantindo não-duplicação

#### Scenario: Chave única em estorno manual
- **WHEN** owner envia POST /payments/{id}/refund com Idempotency-Key
- **THEN** sistema persiste chave no banco e repassa ao MP para idempotência

### Requirement: Publicação de payment.refunded

A API SHALL publicar evento payment.refunded na outbox de forma atômica com a atualização do status, indicando origem (manual ou requested), amount estornado e total refunded. O evento só é publicado quando há sucesso no Mercado Pago e na gravação local.

#### Scenario: payment.refunded publica com origem manual
- **WHEN** owner estorna manualmente
- **THEN** sistema publica payment.refunded com origin=manual, amount estornado, full=(totalRefunded==value)

#### Scenario: payment.refunded publica com origem requested
- **WHEN** order.refund.requested é consumido
- **THEN** sistema publica payment.refunded com origin=requested, amount da cobrança, full=true

#### Scenario: Estorno parcial marca full=false
- **WHEN** owner estorna parcialmente (amount < valor do pagamento)
- **THEN** payment.refunded é publicado com full=false e não muda status do pagamento

#### Scenario: Estorno total marca full=true e muda status
- **WHEN** totalRefunded == payment.value
- **THEN** status muda para refunded e payment.refunded é publicado com full=true

### Requirement: Status mapeado corretamente

A API SHALL atualizar payment.refunded_amount em cada estorno parcial e manter o status como refunded apenas quando totalRefunded == payment.value, não regredindo status se o pagamento já estava refunded.

#### Scenario: Refunded_amount acumula em estornos parciais
- **WHEN** owner estorna primeira vez 50% e depois mais 25%
- **THEN** refunded_amount atualiza para 50%, depois 75%, status permanece em refunded (ou anterior)

#### Scenario: Estorno total não regride status anterior
- **WHEN** pagamento passa pending → captured → refunded
- **THEN** status não regride, permanece refunded
