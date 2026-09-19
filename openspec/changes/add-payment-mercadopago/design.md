# Design

## Context

Existe em `micro-services/payment`: `Payment` (id, idOrder, value, status, provider, externalId,
idempotencyKey, timestamps), `PaymentStatus` (pending, authorized, captured, failed, refunded,
cancelled), entidade, repositório e adapter. `payment.idempotency_key` é `UNIQUE`. Contratos:
`docs/api-contracts.md` §9, `docs/event-contracts.md` §4.6 e §6.

## Goals / Non-Goals

**Goals:** cobrança, webhook e estorno completos, com a saga demonstrável localmente.

**Non-Goals:** boleto; parcelamento com juros calculado por nós (vem do MP); split de pagamento.

## Decisions

### D1. Colunas novas (`V3__payment_details.sql`)
```sql
ALTER TABLE payment
    ADD COLUMN method           VARCHAR(20),
    ADD COLUMN status_detail    VARCHAR(120),
    ADD COLUMN qr_code          TEXT,
    ADD COLUMN qr_code_base64   TEXT,
    ADD COLUMN ticket_url       TEXT,
    ADD COLUMN init_point       TEXT,
    ADD COLUMN expires_at       TIMESTAMPTZ,
    ADD COLUMN card_brand       VARCHAR(30),
    ADD COLUMN card_last4       VARCHAR(4),
    ADD COLUMN installments     SMALLINT,
    ADD COLUMN refunded_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN approved_at      TIMESTAMPTZ;
CREATE INDEX payment_external_idx ON payment (external_id);
```

### D2. Porta do provedor com dois adaptadores
`PaymentGatewayPort` (application): `createPix`, `createCard`, `createPreference`, `get(externalId)`,
`refund(externalId, amount, idempotencyKey)`, `cancel(externalId)`, `paymentMethods()`.
- `MercadoPagoClient` (infra/outbound/external): `RestClient` com `Authorization: Bearer
  {access-token}` e `X-Idempotency-Key`. Endpoints: `POST /v1/payments` (PIX:
  `payment_method_id=pix`, `date_of_expiration` = agora + 30 min; cartão: `token`,
  `payment_method_id`, `issuer_id`, `installments`, `payer`), `POST /checkout/preferences`
  (`items`, `back_urls`, `notification_url`, `expires`), `GET /v1/payments/{id}`,
  `POST /v1/payments/{id}/refunds` (`amount` opcional), `PUT /v1/payments/{id}`
  (`{"status":"cancelled"}`), `GET /v1/payment_methods`. Sempre
  `external_reference` = id local do pagamento.
- `FakePaymentGateway`: ativo quando `app.mercadopago.access-token` está vazio
  (`@ConditionalOnProperty`/fábrica de configuração). Toda cobrança volta `approved` na hora
  (PIX com QR fictício), estorno sempre `refunded`. Loga WARN no boot: "modo fake do Mercado Pago".
Por quê: sem conta MP a saga não seria demonstrável; o contrato HTTP é o mesmo nos dois modos.

### D3. Criação idempotente
`POST /payments` exige `Idempotency-Key`. Existe pagamento com a mesma chave → devolve o mesmo
recurso com `Idempotency-Replayed: true` (mesmo corpo) ou 422 se o `idOrder`/`method` diferem.
Valor sempre de `order GET /internal/orders/{id}` (`totalCost`); `idCustomer != sub` → 403;
pedido não `pending` → 409; já existe pagamento `captured`/`authorized` do pedido → 409.
Fluxo: grava `pending` (transação 1) → chama o provedor fora da transação → grava o resultado e,
se já aprovado, `payment.approved` na outbox (transação 2). Cartão recusado → 201 com
`status=failed` e `payment.failed`.

### D4. Mapeamento de status do MP (única função, usada por webhook, sync e criação)
`pending|in_process|in_mediation` → pending; `authorized` → authorized; `approved` → captured
(`payment.approved`); `rejected` → failed (`payment.failed`); `cancelled` → cancelled
(`payment.failed`); `refunded|charged_back` → refunded (`payment.refunded`). Só publica quando o
status local **muda**; status local à frente do remoto não regride (409 no sync).

### D5. Webhook
`POST /webhooks/mercadopago` (`webhooks:ingest`): dedupe em Redis `payment:webhook:{payload.id}`
(24 h, Redis fora → processa mesmo assim); `type != payment` → 422; busca
`GET /v1/payments/{data.id}` no MP; acha o pagamento local por `external_reference` (id local) e
grava `external_id` do MP; aplica D4. Falha ao gravar/publicar → 500 (o gateway devolve 5xx e o
MP reenvia).

### D6. Estorno
Manual `POST /payments/{id}/refund` (`payments:refund`, `Idempotency-Key` obrigatório) e
automático pelo consumidor de `order.refund.requested` (`docs/event-contracts.md` §4.6), ambos
com `X-Idempotency-Key: refund-{paymentId}` no estorno total. Atualiza `refunded_amount`,
status `refunded` quando total, e grava `payment.refunded` (`full`, `origin`) na outbox.

### D7. Consultas e config
`GET /payments/config` devolve só a public key (nunca o access token), `environment`
(`sandbox`/`production`/`fake`) e métodos habilitados. `GET /payments/methods` com cache Redis
`payment:methods:mp` de 6 h. Consultas de pagamento conferem que o pedido é do cliente
(consulta ao `order`) ou que o chamador é `owner`; senão 404.

### D8. Segurança
`GET /payments/**` → `payments:read`; `POST /payments`, `POST /payments/{id}/cancel` →
`payments:write`; `POST /payments/{id}/refund` → `payments:refund`; `POST /payments/{id}/sync` →
`payments:read` ou `payments:refund`; `/webhooks/**` → `webhooks:ingest`; `/internal/**` →
`internal:hydrate`.

## Risks / Trade-offs

- [Cobrança criada no MP e falha ao gravar o resultado] → o registro `pending` com
  `idempotency_key` já existe; `POST /payments/{id}/sync` e o webhook reconciliam.
- [Modo fake mascara erro de configuração em produção] → WARN no boot e `environment=fake` em
  `/payments/config`.

## Decisoes de implementacao

- **Estrutura de portas**: `PaymentGatewayPort` como abstração única para os adaptadores (MercadoPagoClient e FakePaymentGateway), selecionados via `@ConditionalOnProperty` baseado na presença de `app.mercadopago.access-token`.

- **Outbox transacional**: `OutboxWriter` com `@Transactional(propagation = MANDATORY)` garante que eventos só são gravados se a transação do estado for commitada. Débito de Debezium para publicar via WAL.

- **Controllers stub**: Implementação inicial com endpoints stub compiláveis nas tasks 4.1-4.5; lógica de negócio a ser completada em iteração posterior.

- **Kafka ErrorHandler**: Retry bloqueante com ExponentialBackOff (1s, x2, max 10s, 60s total) preserva ordem por pedido. DeadLetterPublishingRecoverer envia falhas permanentes para `-dlt` com o histórico no header.

- **Segurança**: Regras por rota no SecurityConfig usando `hasAuthority(SCOPE_*)` conforme D8; `/payments/config` é pública (certificado de credencial, não autenticação).

- **Events no consumer**: `OrderRefundRequestedConsumer` com `@KafkaListener` consome eventos de refund do order; lógica de processamento a ser completada.

- **Ambiente fake**: Modo ativado quando `app.mercadopago.access-token` vazio (ou ausente com `matchIfMissing=true`); logs WARN no boot para alertar que não é produção.

- **Usecases na application**: CreatePaymentUseCase (idempotência com UUID, busca ordem, validações, grava pending, chama gateway fora de transação, grava resultado e publica eventos), GetPaymentUseCase (posse), GetConfigUseCase (public key + environment), RefundPaymentUseCase (manual, valida status e saldo, publica evento), SyncPaymentUseCase (relê MP, evita regressão de status), CancelPaymentUseCase (validações), ProcessWebhookUseCase (dedupe Redis, relê MP, aplica mapper, publica eventos).

- **Controllers thin**: PaymentController (traduz HTTP para usecases, valida Idempotency-Key como UUID, verifica posse) com 120 linhas. WebhookController (chamada simples a ProcessWebhookUseCase) com 20 linhas.

- **Consumidor com lógica**: OrderRefundRequestedConsumer (56 linhas) processa event, valida payload, checa status, chamada RefundPaymentUseCase com chave determinística refund-{paymentId} para idempotência no MP.

- **SecurityConfig atualizado**: /payments/config requer SCOPE_payments:read (sem permitAll); /internal/** requer SCOPE_internal:hydrate; webhook requer SCOPE_webhooks:ingest. Nenhuma rota pública no serviço (o gateway emite token com escopos mesmo para anônimo).

### Correções da revisão

- Seleção do provedor: `@ConditionalOnProperty` com regex não funciona (a anotação não aceita
  regex), e com token configurado nenhum provedor era criado. Virou `PaymentGatewayConfig`, que
  escolhe explicitamente entre `MercadoPagoClient` e `FakePaymentGateway` pelo token.
- `CurrentUser` era `@RequestScope` pedindo um bean `Jwt` que não existe; passou a ler o token do
  `SecurityContext`. O papel `owner` vem da claim `roles` (não existe autoridade `SCOPE_owner`).
- Respostas passam por `PaymentResponse`: a chave de idempotência nunca sai na API e dinheiro vai
  como string.
- `GET /payments?orderId=` e `GET /payments/methods` devolviam vazio; implementados
  (`ListPaymentsUseCase` com checagem de posse, `GetPaymentMethodsUseCase` com cache Redis de 6 h).
  Criado `GET /internal/payments?orderId=`.
- Removido o header fixo `Idempotency-Replayed: false`, que mentiria num replay.
