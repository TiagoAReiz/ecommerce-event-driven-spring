# Proposal

## Why

O `user` só atende o login (buscar por e-mail e criar). Sem perfil, sem endereços e sem as rotas
internas, o checkout não tem destino, o frete não tem CEP e o gateway não consegue montar o
papel do usuário a cada requisição.

## What Changes

- Perfil próprio: `GET /users/me`, `PATCH /users/me` (nome, CPF, telefone, foto),
  `DELETE /users/me` (soft delete + evento `user.deleted`).
- Perfil público reduzido: `GET /users/{id}`.
- Endereços: listar, detalhar, criar, remover e editar. **Endereço é imutável**: `PUT`/`PATCH`
  criam um endereço novo e removem o antigo, devolvendo o novo id.
- Rotas internas: `GET /internal/users?ids=`, `GET /internal/users/{id}/profile`,
  `GET /internal/addresses/{id}?userId=` (enxerga endereço removido).
- `roles` na resposta de `GET /users?email=` já existe; mantido.
- Kit de plataforma: ProblemDetail, regras de escopo por rota, outbox (Debezium) e tópico
  `ecommerce.user.deleted.v1`, variáveis de ambiente do contrato do compose.
- **Removido do contrato:** rotas de vendedor (`/users/me/owner`, `/owners/{id}`) — loja única.

## Capabilities

### New Capabilities
- `user-profile`: perfil do usuário (próprio e público), edição, remoção de conta e o evento de remoção.
- `user-addresses`: endereços de entrega imutáveis do usuário.
- `user-internal-api`: leituras servidor-a-servidor de usuário, perfil com papéis e endereço.

### Modified Capabilities

## Impact

- `micro-services/user/**` apenas. Migration nova `V3__outbox.sql`.
- Consumidores: gateway (`/internal/users/{id}/profile`), inventory (`/internal/users`),
  order e shipment (`/internal/addresses/{id}`), inventory e order (`user.deleted`).
