# Spec Delta

## Purpose

Processar webhooks do Mercado Pago com deduplicação, releitura da verdade e mapeamento de status, publicando eventos de pagamento de forma atômica.

## ADDED Requirements

### Requirement: Webhook com deduplicação em Redis

A API SHALL receber POST /webhooks/mercadopago (gateway repassa com escopo webhooks:ingest) e deduplicar eventos em Redis (chave payment:webhook:{mpEventId}, TTL 24h) para evitar processar o mesmo webhook duas vezes. Se Redis falhar, o webhook é processado mesmo assim (Redis é otimização, não dependência).

#### Scenario: Primeiro recebimento do webhook
- **WHEN** gateway repassa POST /webhooks/mercadopago com payload do MP
- **THEN** sistema checa dedupe em Redis, não encontra, processa e grava payment:webhook:{id} com TTL 24h

#### Scenario: Reentrega do webhook em menos de 24h
- **WHEN** MP reenvia mesmo webhook dentro de 24h
- **THEN** sistema encontra dedupe em Redis e retorna 200 sem reprocessar

#### Scenario: Redis fora, webhook é processado mesmo assim
- **WHEN** Redis indisponível e webhook chega
- **THEN** sistema loga WARN e processa webhook normalmente (queda de cache não bloqueia)

### Requirement: Releitura da verdade no Mercado Pago

A API SHALL validar que o payload do webhook é tipo payment (rejeitar outros com 422), buscar GET /v1/payments/{data.id} no Mercado Pago para reler a verdade, e nunca usar os dados brutos do webhook como fonte de verdade.

#### Scenario: Webhook tipo payment é processado
- **WHEN** webhook com type=payment chega
- **THEN** sistema chama GET /v1/payments/{id} no MP, valida assinatura e processa

#### Scenario: Webhook tipo order é rejeitado
- **WHEN** webhook com type=order chega
- **THEN** sistema retorna 422

#### Scenario: Busca no MP falha, retorna 5xx
- **WHEN** GET /v1/payments/{id} retorna erro do MP
- **THEN** webhook handler retorna 5xx (gateway reenvia)

### Requirement: Encontrar pagamento local por external_reference

A API SHALL localizar o pagamento local buscando na tabela pela coluna external_id (ID do MP) que foi gravado quando a cobrança foi criada. Se não encontrar, trata como webhook órfão (não publica, loga).

#### Scenario: Pagamento localizado e status atualizado
- **WHEN** webhook chega com payment.id=119384756201 já armazenado em local payment.external_id
- **THEN** sistema encontra a linha e aplica o mapeamento de status

#### Scenario: Pagamento não encontrado localmente
- **WHEN** webhook chega com payment.id nunca visto (órfão)
- **THEN** sistema loga WARN "orphan webhook" e não publica eventos

### Requirement: Mapeamento de status conforme contrato

A API SHALL mapear status do MP conforme definido em event-contracts.md §9.2: pending/in_process/in_mediation → pending; authorized → authorized; approved → captured; rejected/cancelled → failed; refunded/charged_back → refunded. Publicar evento apenas quando status local **muda**, nunca quando regride.

#### Scenario: Webhook pending muda para approved
- **WHEN** webhook chega com MP status=approved para pagamento local com status=pending
- **THEN** sistema atualiza status para captured, publica payment.approved

#### Scenario: Webhook com status local já à frente não regride
- **WHEN** webhook chega com status=pending para pagamento local já captured
- **THEN** sistema não regride status, nada é publicado

#### Scenario: Webhook cancelled mapeia para failed
- **WHEN** webhook chega com MP status=cancelled
- **THEN** sistema mapeia para failed local e publica payment.failed

#### Scenario: Webhook refunded publica payment.refunded
- **WHEN** webhook chega com MP status=refunded
- **THEN** sistema mapeia para refunded local, calcula refunded_amount e publica payment.refunded

### Requirement: Publicação de eventos de forma atômica

A API SHALL publicar payment.approved, payment.failed ou payment.refunded na outbox de forma atômica com a atualização do status local, garantindo que evento e banco mudam juntos ou nenhum dos dois.

#### Scenario: payment.approved é publicado atomicamente
- **WHEN** webhook causa transição para captured
- **THEN** status é gravado e payment.approved é publicado na outbox na mesma transação

#### Scenario: Falha ao gravar status volta 500
- **WHEN** erro de banco ao atualizar payment
- **THEN** publicação da outbox não ocorre, webhook handler retorna 500

#### Scenario: payment.failed é publicado quando MP recusa
- **WHEN** webhook com status=rejected chega
- **THEN** sistema grava status=failed e publica payment.failed, tudo atômico

#### Scenario: payment.refunded indica origem
- **WHEN** webhook com status=refunded chega
- **THEN** payment.refunded é publicado com origin=chargeback (não manual, não requested)

### Requirement: Retorno 5xx para provocar reenvio

A API SHALL retornar 5xx quando houver erro ao gravar o pagamento ou publicar o evento, para que o Mercado Pago reaplique a política de reentrega (exponencial com máximo 60s total).

#### Scenario: Falha de banco retorna 500
- **WHEN** erro de transação ao gravar status
- **THEN** webhook handler retorna 500, conexão fecha, MP reenvia

#### Scenario: Falha ao serializar evento retorna 500
- **WHEN** erro ao montar payload do evento para a outbox
- **THEN** webhook handler retorna 500

#### Scenario: Webhook bem-sucedido retorna 200
- **WHEN** pagamento gravado e evento publicado com sucesso
- **THEN** webhook handler retorna 200, MP marca como entregue

### Requirement: Validação de assinatura pelo gateway

A API SHALL receber o webhook apenas através do gateway (POST /webhooks/mercadopago repassa) com header de assinatura já validado (signatureVerified=true), nunca aceitando webhooks diretos de fora. Rejeitar webhooks com signatureVerified=false com 401.

#### Scenario: Webhook com assinatura validada
- **WHEN** gateway repassa POST /webhooks/mercadopago com signatureVerified=true
- **THEN** sistema processa normalmente

#### Scenario: Webhook com assinatura inválida
- **WHEN** webhook com signatureVerified=false chega
- **THEN** sistema retorna 401

### Requirement: Guarda de status inconsistente

A API SHALL não processar webhook se o status do MP for anterior ao status local (ex.: webhook chega com pending depois de já ter capturado). Sistema retorna 409 neste caso, mas não reenvia (política).

#### Scenario: Webhook antigo depois de novo
- **WHEN** webhook com status=pending chega para pagamento já com status=captured
- **THEN** sistema retorna 409, não processa, não publica

### Requirement: Informações do webhook salvas no pagamento

A API SHALL gravar external_id do MP (se novo), status_detail do MP para exibir ao usuário, e datas como approved_at quando o pagamento foi aprovado no MP.

#### Scenario: Webhook atualiza external_id na primeira vez
- **WHEN** webhook chega para pagamento criado com sucesso mas external_id não estava gravado
- **THEN** sistema preenche external_id do payload

#### Scenario: Status detail preservado para exibição
- **WHEN** webhook chega com status_detail do MP
- **THEN** sistema grava status_detail local para exibir motivo ao cliente (ex.: cc_rejected_insufficient_amount)

#### Scenario: Approved_at gravado da resposta do MP
- **WHEN** webhook chega com status=approved e date_approved do MP
- **THEN** sistema grava approved_at = date_approved do MP
