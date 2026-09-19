# Spec Delta

## Purpose

Criar, consultar e gerenciar cobranças no Mercado Pago de forma idempotente, com suporte a PIX, cartão tokenizado e Checkout Pro.

## ADDED Requirements

### Requirement: Criar cobrança PIX idempotente

A API SHALL permitir criar uma cobrança PIX na conta do Mercado Pago de forma idempotente, retornando um QR Code com prazo de validade de 30 minutos. O cliente MUST fornecer a chave de idempotência (Idempotency-Key) e o ID do pedido. Requisições repetidas com a mesma chave e mesmo corpo devem retornar o mesmo recurso com o header Idempotency-Replayed: true.

#### Scenario: Criar PIX com sucesso na primeira tentativa
- **WHEN** cliente envia POST /payments com method=pix e Idempotency-Key válido
- **THEN** sistema retorna 201 com status=pending e QR code em detail (qrCode, qrCodeBase64, ticketUrl, expiresAt)

#### Scenario: Reenvio idempotente do PIX
- **WHEN** cliente reenvia POST /payments com o mesmo Idempotency-Key e corpo idêntico
- **THEN** sistema retorna 201 com Idempotency-Replayed: true e os mesmos dados da primeira requisição

#### Scenario: PIX com chave idempotência reutilizada com corpo diferente
- **WHEN** cliente envia POST /payments com Idempotency-Key já usado, mas idOrder ou method diferentes
- **THEN** sistema retorna 422 com code=IDEMPOTENCY_KEY_REUSED

### Requirement: Criar cobrança com cartão tokenizado idempotente

A API SHALL permitir criar uma cobrança com cartão tokenizado de forma idempotente. O front tokeniza o PAN com o SDK do Mercado Pago e envia apenas o token de uso único, issuerId, payment_method_id e installments. A cobrança é criada no Mercado Pago de forma idempotente através do header X-Idempotency-Key.

#### Scenario: Criar pagamento com cartão aprovado
- **WHEN** cliente envia POST /payments com method=credit_card, token válido e installments
- **THEN** sistema retorna 201 com status=captured, detail contendo brand e last4

#### Scenario: Cartão recusado retorna 201 com status failed
- **WHEN** cliente envia POST /payments com cartão que será recusado pela bandeira
- **THEN** sistema retorna 201 com status=failed e statusDetail indicando motivo (cc_rejected_insufficient_amount, etc)

#### Scenario: Reenvio idempotente do cartão
- **WHEN** cliente reenvia POST /payments com o mesmo Idempotency-Key e corpo do cartão
- **THEN** sistema retorna 201 com Idempotency-Replayed: true e mesmo resultado anterior

### Requirement: Criar Checkout Pro idempotente

A API SHALL permitir criar uma preference no Mercado Pago que redireciona o cliente para a página de checkout do Mercado Pago, retornando um initPoint com prazo de validade. A cobrança é idempotente através da chave de idempotência.

#### Scenario: Criar Checkout Pro com sucesso
- **WHEN** cliente envia POST /payments com method=checkout_pro e Idempotency-Key
- **THEN** sistema retorna 201 com status=pending, externalId da preference e detail.initPoint contendo URL de checkout

#### Scenario: Reenvio idempotente do Checkout Pro
- **WHEN** cliente reenvia POST /payments com mesmo Idempotency-Key e corpo
- **THEN** sistema retorna 201 com Idempotency-Replayed: true e mesma initPoint

### Requirement: Valor sempre vem do pedido

A API SHALL sempre ler o valor total da cobrança de GET /internal/orders/{id} (totalCost), nunca aceitando valor do corpo da requisição POST /payments. O sistema MUST validar que o pedido existe e pertence ao usuário autenticado.

#### Scenario: POST /payments ignora value no corpo
- **WHEN** cliente envia POST /payments com method=pix e um "value" diferente do totalCost do pedido
- **THEN** sistema cria cobrança com o totalCost do pedido, ignorando o value do corpo

#### Scenario: Pedido inexistente retorna 404
- **WHEN** cliente envia POST /payments com idOrder que não existe
- **THEN** sistema retorna 404

### Requirement: Pedido de outro cliente retorna 403

A API SHALL verificar que o pedido pertence ao usuário autenticado (idCustomer == sub do token) e retornar 403 caso contrário.

#### Scenario: Cliente tenta pagar pedido de outro cliente
- **WHEN** cliente autenticado como usuario A envia POST /payments para pedido de usuario B
- **THEN** sistema retorna 403

### Requirement: Pedido não pending retorna 409

A API SHALL rejeitar criação de cobrança se o pedido não estiver em status pending, ou se já existe pagamento captured/authorized do pedido.

#### Scenario: POST /payments para pedido não pending
- **WHEN** cliente envia POST /payments para pedido com status=cancelled
- **THEN** sistema retorna 409 com code=ORDER_NOT_PENDING

#### Scenario: POST /payments para pedido com pagamento já capturado
- **WHEN** cliente envia POST /payments para pedido que já tem pagamento com status=captured
- **THEN** sistema retorna 409 com code=PAYMENT_ALREADY_CAPTURED

### Requirement: Requisição em voo retorna 409

A API SHALL detectar tentativas de criar duas cobranças do mesmo pedido em paralelo e retornar 409 IDEMPOTENCY_IN_FLIGHT com Retry-After para o segundo.

#### Scenario: Duas requisições paralelas do mesmo pedido
- **WHEN** cliente envia simultaneamente dois POST /payments para o mesmo pedido (chaves diferentes)
- **THEN** primeira requisição processa normalmente, segunda retorna 409 com Retry-After: 1

### Requirement: Consultar configuração de pagamento

A API SHALL expor GET /payments/config como público, devolvendo apenas a public key do Mercado Pago, locale, currency, environment (sandbox/production/fake) e métodos habilitados.

#### Scenario: GET /payments/config devolve config pública
- **WHEN** cliente faz GET /payments/config
- **THEN** sistema retorna 200 com publicKey (nunca access_token), locale=pt-BR, currency=BRL, environment, enabledMethods

#### Scenario: GET /payments/config sem credencial do MP
- **WHEN** MP_PUBLIC_KEY não está configurado
- **THEN** sistema retorna 500

### Requirement: Métodos de pagamento com cache de 6 horas

A API SHALL expor GET /payments/methods que cacheia a resposta de GET /v1/payment_methods do Mercado Pago por 6 horas em Redis (payment:methods:mp). Suporta query param ?amount para calcular parcelamento.

#### Scenario: GET /payments/methods com cache Redis vivo
- **WHEN** cliente faz GET /payments/methods e cache contém dados frescos
- **THEN** sistema retorna 200 com métodos do Redis, sem chamar o MP (Cache-Control: max-age)

#### Scenario: GET /payments/methods com cache expirado
- **WHEN** cliente faz GET /payments/methods e cache expirou
- **THEN** sistema chama GET /v1/payment_methods do MP, cacheia por 6h e retorna 200

#### Scenario: GET /payments/methods com amount para parcelamento
- **WHEN** cliente envia GET /payments/methods?amount=1000.00
- **THEN** sistema retorna 200 com installments calculados pelo MP para cada bandeira

### Requirement: Consultar pagamento por ID

A API SHALL permitir consultar um pagamento criado através de GET /payments/{id}, retornando status, valor, detalhe do método (QR, initPoint, brand/last4), data de aprovação. O usuário SÃO pode consultar seus próprios pagamentos ou qualquer pagamento se for owner.

#### Scenario: Cliente consulta seu próprio pagamento
- **WHEN** cliente autenticado envia GET /payments/{id} onde o pagamento pertence a seu pedido
- **THEN** sistema retorna 200 com dados completos do pagamento

#### Scenario: Cliente consulta pagamento de outro
- **WHEN** cliente autenticado envia GET /payments/{id} de pagamento de outro cliente
- **THEN** sistema retorna 404

#### Scenario: Owner consulta qualquer pagamento
- **WHEN** owner autenticado envia GET /payments/{id}
- **THEN** sistema retorna 200 com dados do pagamento

### Requirement: Consultar pagamentos por pedido

A API SHALL permitir listar todas as tentativas de pagamento de um pedido através de GET /payments?orderId={id}, retornando array paginado de pagamentos com seus status e detalhes.

#### Scenario: GET /payments?orderId lista tentativas do pedido
- **WHEN** cliente envia GET /payments?orderId=3301
- **THEN** sistema retorna 200 com array de pagamentos do pedido (content, page meta)

#### Scenario: Pedido de outro cliente retorna 404
- **WHEN** cliente envia GET /payments?orderId de pedido de outro
- **THEN** sistema retorna 404

### Requirement: Sincronizar pagamento com Mercado Pago

A API SHALL expor POST /payments/{id}/sync que relê o status do pagamento no Mercado Pago (GET /v1/payments/{externalId}) e aplica o mapeamento de status local. Usa-se para reconciliação após webhook perdido ou timeout.

#### Scenario: Sync atualiza status quando mudou no MP
- **WHEN** cliente envia POST /payments/{id}/sync e pagamento mudou de pending para captured no MP
- **THEN** sistema retorna 200 com changed: true, atualiza status local e publica payment.approved

#### Scenario: Sync não regride status local
- **WHEN** cliente envia POST /payments/{id}/sync e status local está à frente do MP
- **THEN** sistema retorna 409 com code=STATUS_REGRESSION_PREVENTED

#### Scenario: Sync limite de 1 por minuto
- **WHEN** cliente envia dois POST /payments/{id}/sync do mesmo pagamento em 30s
- **THEN** segunda requisição retorna 429

### Requirement: Cancelar cobrança

A API SHALL expor POST /payments/{id}/cancel que cancela cobrança ainda não paga (PIX vencido, preference aberta) através de PUT /v1/payments/{externalId} com status=cancelled no MP.

#### Scenario: Cancelar PIX não pago
- **WHEN** cliente envia POST /payments/{id}/cancel para PIX com status=pending
- **THEN** sistema retorna 200, cancela no MP e atualiza status local para cancelled

#### Scenario: Cancelar cartão já capturado retorna 409
- **WHEN** cliente envia POST /payments/{id}/cancel para cartão com status=captured
- **THEN** sistema retorna 409 com code=PAYMENT_ALREADY_CAPTURED (usar refund, não cancel)

#### Scenario: Requer escopo payments:write
- **WHEN** cliente sem payments:write envia POST /payments/{id}/cancel
- **THEN** sistema retorna 403

### Requirement: Modo fake quando credencial não configurada

A API SHALL ativar modo fake (FakePaymentGateway via @ConditionalOnProperty) quando MP_ACCESS_TOKEN está vazio, aprovando toda cobrança na hora com QR fictício. Sistema MUST logar WARN no boot "modo fake do Mercado Pago" e retornar environment=fake em GET /payments/config.

#### Scenario: Modo fake aprova toda cobrança PIX
- **WHEN** MP_ACCESS_TOKEN não está configurado e cliente cria cobrança PIX
- **THEN** sistema retorna 201 com status=pending (não captured, para simular webhook) e QR fictício

#### Scenario: Modo fake identifica-se em /config
- **WHEN** MP_ACCESS_TOKEN não configurado
- **THEN** GET /payments/config retorna environment=fake e enabledMethods permite teste local

#### Scenario: Modo fake em estorno
- **WHEN** modo fake e cliente estorna cobrança
- **THEN** sistema retorna 200 imediatamente com status=refunded, sem chamar MP

### Requirement: Autenticação e autorização

A API SHALL exigir token com os escopos corretos: GET /payments/** requer payments:read; POST /payments e POST /payments/{id}/cancel requerem payments:write; POST /payments/{id}/refund requer payments:refund; POST /payments/{id}/sync requer payments:read ou payments:refund; GET /internal/payments requer internal:hydrate; POST /webhooks/* requer webhooks:ingest.

#### Scenario: Sem escopo retorna 403
- **WHEN** cliente sem payments:write envia POST /payments
- **THEN** sistema retorna 403

#### Scenario: Token interno requer internal:hydrate
- **WHEN** serviço upstream chama GET /internal/payments?orderId=123
- **THEN** sistema valida escopo internal:hydrate e retorna 200 ou 403

### Requirement: Idempotência persistida no banco

A API SHALL gravar payment.idempotency_key (UNIQUE) em cada cobrança e repassá-la ao Mercado Pago no header X-Idempotency-Key, garantindo que a idempotência atravessa o provedor.

#### Scenario: Mesma chave não cria duplicata no MP
- **WHEN** cliente reenvia POST /payments com Idempotency-Key já processado
- **THEN** MP identifica a chave e devolve o pagamento anterior, e sistema não cria nova linha
