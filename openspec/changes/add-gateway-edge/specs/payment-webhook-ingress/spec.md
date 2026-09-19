# Spec Delta

## Purpose

O gateway SHALL receber, validar (HMAC com janela de replay de 5 minutos) e repassar webhooks do Mercado Pago para o serviço `payment`, preservando a integridade e mapeando a autorização para o escopo interno `webhooks:ingest`.

## ADDED Requirements

### Requirement: POST /public/webhooks/mercadopago valida assinatura HMAC-SHA256
O gateway SHALL fornecer um endpoint `POST /public/webhooks/mercadopago` que receba payloads do Mercado Pago sem autenticação de token (será validada por HMAC). O gateway SHALL validar a assinatura SHA256 construindo o manifest `id:<data.id>;request-id:<x-request-id>;ts:<ts>;` em minúsculas com ponto-e-vírgula final, comparando `HMAC-SHA256(manifest, MP_WEBHOOK_SECRET)` com o valor `v1` do header `x-signature` em tempo constante.

#### Scenario: Webhook válido com assinatura correta é aceito
- **WHEN** uma requisição POST chega em `/public/webhooks/mercadopago` com `x-signature: ts=1789564800,v1=abc123…` e HMAC válido
- **THEN** o gateway aceita (prossegue para repasse)

#### Scenario: HMAC inválido retorna 401
- **WHEN** uma requisição chega com `x-signature` cujo valor `v1` não confere com o manifest
- **THEN** o gateway responde `401` (não repassar webhook falso)

#### Scenario: Header x-signature ausente retorna 401
- **WHEN** uma requisição chega sem header `x-signature`
- **THEN** o gateway responde `401`

#### Scenario: Comparação de HMAC usa tempo constante
- **WHEN** um HMAC incorreto é comparado com o correto
- **THEN** a comparação leva tempo constante para evitar ataques de timing

### Requirement: Rejeição de replay attack com janela de 5 minutos
O gateway SHALL validar o timestamp `ts` (em segundos) extraído do header `x-signature`. Se `|now - ts| > 5 minutos`, SHALL responder `408 Request Timeout` para indicar que o webhook é muito antigo ou muito futuro (replay attack).

#### Scenario: Webhook dentro da janela de 5 minutos é aceito
- **WHEN** o timestamp do webhook diferencia menos de 5 minutos do agora
- **THEN** o gateway processa normalmente

#### Scenario: Webhook fora da janela retorna 408
- **WHEN** o timestamp do webhook diferencia mais de 5 minutos do agora (passado ou futuro)
- **THEN** o gateway responde `408` (não repassar, MP vai reenviar)

### Requirement: Extração de data.id de query string ou corpo
O gateway SHALL extrair `data.id` do webhook: se presente na query string `?data.id=…`, use este; caso contrário, use `data.id` do corpo JSON. Se ausente em ambos, SHALL responder `400`.

#### Scenario: data.id na query string é usado
- **WHEN** uma requisição chega em `/public/webhooks/mercadopago?data.id=119384756201` com corpo válido
- **THEN** o gateway usa este `data.id` para validação e repasse

#### Scenario: data.id no corpo é usado se ausente na query
- **WHEN** uma requisição chega sem query e o corpo contém `{"data":{"id":"119384756201"}}`
- **THEN** o gateway usa este `data.id`

#### Scenario: data.id ausente em ambos retorna 400
- **WHEN** uma requisição chega sem `data.id` em query ou corpo
- **THEN** o gateway responde `400`

### Requirement: Validação de type obrigatório
O gateway SHALL validar que o campo `type` existe na query string ou no corpo. Tipos reconhecidos: `payment`. Qualquer outro tipo (`plan`, `subscription`, `invoice`) SHALL retornar `422` para indicar que não será reprocessado.

#### Scenario: Type payment é processado
- **WHEN** uma requisição chega com `type: "payment"`
- **THEN** o gateway prossegue para repasse

#### Scenario: Type desconhecido retorna 422
- **WHEN** uma requisição chega com `type: "plan"`
- **THEN** o gateway responde `422` (MP saberá não reenviar)

#### Scenario: Type ausente retorna 400
- **WHEN** uma requisição chega sem field `type`
- **THEN** o gateway responde `400`

### Requirement: Repasse para payment com token internal e escopo webhooks:ingest
O gateway SHALL fazer um POST para `payment:8084 POST /webhooks/mercadopago` com o corpo original do Mercado Pago envelopado como `{signatureVerified:true, receivedAt:<timestamp>, payload:<corpo original>}` e um token interno com `sub=svc:api-gateway`, escopos `webhooks:ingest internal:hydrate` e TTL 5 minutos.

#### Scenario: Webhook repassado ao payment com token correto
- **WHEN** a validação HMAC e timestamp passam
- **THEN** o gateway faz POST ao payment com corpo envelopado e token `aud=internal`

#### Scenario: Token repasse tem escopos webhooks:ingest e internal:hydrate
- **WHEN** o gateway emite token para repassar webhook
- **THEN** o token contém `scope: "webhooks:ingest internal:hydrate"`

### Requirement: Tratamento de respostas e retry do Mercado Pago
O gateway SHALL responder `200` em qualquer uma das seguintes situações: sucesso (2xx do payment), erro de negócio que o MP não deveria reenviar (404, 409, 422 do payment) ou evento duplicado já processado. Erros que devem causar retry (5xx ou timeout) SHALL ser respondidos como `500` ou `503` para que o Mercado Pago reenvie automaticamente.

#### Scenario: Sucesso 2xx do payment resulta em resposta 200 ao MP
- **WHEN** o payment responde `200` ou `201`
- **THEN** o gateway responde `200` (MP para de reenviar)

#### Scenario: Erro de negócio 404 do payment resulta em resposta 200 ao MP
- **WHEN** o payment responde `404` (recurso não encontrado)
- **THEN** o gateway responde `200` (não adianta MP reenviar)

#### Scenario: Erro de negócio 409 do payment resulta em resposta 200 ao MP
- **WHEN** o payment responde `409` (conflito de negócio)
- **THEN** o gateway responde `200`

#### Scenario: Erro de negócio 422 do payment resulta em resposta 200 ao MP
- **WHEN** o payment responde `422` (dado inválido)
- **THEN** o gateway responde `200`

#### Scenario: Erro de servidor 500 do payment causa resposta 500 ao MP
- **WHEN** o payment responde `500`
- **THEN** o gateway responde `500` (MP vai reenviar)

#### Scenario: Indisponibilidade do payment causa resposta 503 ao MP
- **WHEN** o payment está indisponível (conexão recusada ou timeout)
- **THEN** o gateway responde `503` (MP vai reenviar)

#### Scenario: Evento duplicado já processado retorna 200
- **WHEN** o mesmo `data.id` chega duas vezes
- **THEN** o gateway responde `200` (dedupe silencioso)

### Requirement: Corpo vazio em resposta para todos os 2xx
O gateway SHALL responder com corpo vazio (sem JSON) em todas as respostas `200` a `POST /public/webhooks/mercadopago`. O Mercado Pago exige apenas status 2xx para saber que o webhook foi processado.

#### Scenario: Resposta 200 tem corpo vazio
- **WHEN** um webhook é processado com sucesso
- **THEN** o gateway responde `200` com corpo vazio
