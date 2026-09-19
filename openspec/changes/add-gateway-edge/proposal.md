# Proposal

## Why

O gateway hoje só faz o login com o Google e publica o JWKS. Nenhuma rota de negócio chega aos
serviços, porque não existe proxy nem troca do token do browser pelo token interno, e os
serviços não têm como obter credencial para conversar entre si. Sem a borda, nenhum dos
outros changes é utilizável de fora.

## What Changes

- Proxy de `/api/v1/**` para os cinco serviços, roteado pelo primeiro segmento
  (`docs/api-contracts.md` §3.1), trocando o token do browser (`aud=front`) por um token
  interno novo (`aud=internal`) com `sub`, `roles` e os escopos do papel.
- Rotas públicas atendidas sem token do usuário (§2.4), com token interno de anônimo.
- `GET /api/v1/auth/session`, `POST /api/v1/auth/refresh` (renovação deslizante, teto de 7 dias
  por `auth_time`) e `POST /api/v1/auth/logout` (denylist no Redis).
- `POST /auth/service-token`: credencial de cliente por serviço → token interno com
  `internal:hydrate`, usado em toda chamada servidor-a-servidor.
- `POST /public/webhooks/mercadopago`: valida a assinatura HMAC do Mercado Pago e repassa ao
  `payment` com escopo `webhooks:ingest`.
- Login: token entregue em fragmento (`/callback#token=`), com claims `roles` e `auth_time`.
- Cache de perfil (`auth:profile:{id}`), rate limit, `X-Request-Id`, CORS do front e
  ProblemDetail em todo erro.
- **Removido:** webhook de transportadora (não há transportadora integrada).

## Capabilities

### New Capabilities
- `edge-gateway`: roteamento público, troca de token, rotas públicas, rate limit e isolamento das rotas internas.
- `session-auth`: sessão do usuário — login Google, sessão atual, renovação deslizante, logout.
- `service-auth`: emissão de token de serviço por credencial de cliente.
- `payment-webhook-ingress`: recepção e validação dos webhooks do Mercado Pago.

### Modified Capabilities

## Impact

- `micro-services/api-gateway/**` apenas.
- Contrato consumido pelos outros serviços: `POST /auth/service-token` e claims do token interno
  (`openspec/config.yaml`, seção AUTENTICACAO E ESCOPOS).
- Contrato consumido do `user`: `GET /users?email=` passa a trazer `roles`;
  `GET /internal/users/{id}/profile` (entregue por `add-user-service-api`).
- Nova dependência: Redis (denylist, cache de perfil, rate limit).
