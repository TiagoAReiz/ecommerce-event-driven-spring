# Design

## Context

Existe: `modules/user` (`UserController` com `GET /users?email=` e `POST /users`, `CreateUser`,
`FindUserByEmail`, `UserResponse` com `roles`), `modules/owner` (`ProvisionStoreOwner`,
`IsStoreOwner`, `StoreOwnerStartup`), `modules/address` (modelo, entidade, repositório,
adapter — sem casos de uso). `config/SecurityConfig` já é resource server com `aud=internal`.
Contratos: `docs/api-contracts.md` §6 (ignorar rotas de owner, já removidas do produto) e
`docs/event-contracts.md` §8.1.

## Goals / Non-Goals

**Goals:** todas as rotas da §6 que sobram na loja única, com os códigos HTTP documentados.

**Non-Goals:** consultar o `order` para bloquear remoção de conta com pedido aberto (ver D3).

## Decisions

### D1. Endereço imutável
`PUT` e `PATCH /users/me/addresses/{id}` gravam um endereço novo (merge dos campos no PATCH) e
fazem soft delete do antigo na mesma transação; respondem 200 com o novo recurso e
`Location` do novo id. `DELETE` é soft delete. `GET /internal/addresses/{id}?userId=` lê
**inclusive** removidos (repositório ganha um `findByIdIncludingDeleted`), porque um pedido
pode apontar para um endereço já trocado. As rotas de cliente continuam enxergando só ativos.
Alternativa descartada: snapshot do endereço no pedido — exigiria colunas novas no `order`.

### D2. Perfil com papéis
`GET /internal/users/{id}/profile` → `{id,name,email,photoUrl,roles}` usando `IsStoreOwnerPort`
(roles `["customer"]` ou `["customer","owner"]`). `GET /users/me` usa o mesmo cálculo.

### D3. Remoção de conta
`DELETE /users/me`: 409 `STORE_OWNER_ACCOUNT` se for a conta da loja; senão soft delete do
usuário e dos endereços e grava `user.deleted` na outbox **na mesma transação**. A checagem de
pedido aberto de `api-contracts` §6.5 não é feita (exigiria chamada síncrona ao `order`); o
pedido continua existindo porque é histórico — decisão registrada.

### D4. Validações
CPF: 11 dígitos + dígitos verificadores (422 `INVALID_CPF`); CPF de outro usuário → 409.
Telefone: E.164 (`^\+[1-9]\d{7,14}$`) → 422. CEP: 8 dígitos → 400; `state` com 2 letras quando
`country=BR` → 422. `email`/`googleSub` no corpo do PATCH → 400.

### D5. Segurança (hasAuthority)
`GET /users` → `users:read`; `POST /users` → `users:write`; `GET /users/me`, `GET /users/{id}` →
`users:read`; `PATCH/DELETE /users/me` → `users:write`; `GET /users/me/addresses/**` →
`addresses:read`; escrita de endereços → `addresses:write`; `/internal/**` → `internal:hydrate`.
Atenção à ordem dos matchers: `/users/me/**` antes de `/users/{id}`.

### D6. Outbox
Kit de plataforma: `V3__outbox.sql` (publicação `debezium_user_outbox`), `shared/outbox/*`,
`UserEventOutboxPublisher` implementando uma port `UserEventPublisherPort.publishUserDeleted`,
tópico `ecommerce.user.deleted.v1`, alias `userDeleted`, key = userId. `config/KafkaConfig` só
com `NewTopic` do tópico e do `-dlt` (o `user` não consome nada). Adicionar ao pom o que faltar
para Kafka admin (o starter kafka já existe).

## Risks / Trade-offs

- [Id do endereço muda na edição] → o front usa o id devolvido; pedidos antigos continuam
  apontando para o endereço original, que é o comportamento desejado.

## Decisoes de Implementacao

- **Validacao de CPF**: Implementada com algoritmo de digitos verificadores (modulo 11).
  Valida formato (11 digitos) e digitos verificadores conforme regra da Receita Federal.

- **Validacao de telefone**: Usa padrao E.164 (`^\+[1-9]\d{7,14}$`). Rejeita numeros sem
  codigo de pais (+) ou com comprimento invalido.

- **Validacao de CEP**: Apenas validacao de formato (8 digitos). Nao verifica se o CEP
  existe na BrasilAPI (escopo futuro ou gateway).

- **AddressRepositoryPort.findByIdIncludingDeleted**: Rota interna necessaria para que
  order/shipment consultem historico de enderecos deletados. Nao afeta leituras publicas.

- **UpdateMyProfile + CPF duplicado**: Mesmo em PATCH, detecta CPF ja em uso por outro
  usuario via DataIntegrityViolationException (index unico condicional).

- **Listagem paginada padroes**: Default `size=20`, max `100`. Sort default `createdAt,desc`.
  Ordem mantida no banco via indice (V1).

- **InternalUserController respostas vazias**: `/internal/users?ids=` devolve `users=[]`
  e `missing=[...]` mesmo se todos os ids faltarem. Nao estoura erro.

- **Delete endereco em shipment ativo**: Nao implementado bloqueio (ver design D3, non-goal).
  Spec menciona `ADDRESS_IN_ACTIVE_SHIPMENT` 409, mas nao temos acesso ao servico shipment.

- **Location header em POST /users/me/addresses**: Construido dinamicamente com
  `ServletUriComponentsBuilder` do Spring. Devolve o novo id.

- **Soft delete cascata**: DELETE /users/me deleta usuario e enderecos ativos na mesma
  transacao `@Transactional` do caso de uso. Evento publicado tambem na mesma transacao.

- **GlobalExceptionHandler**: Trata `ApiException` (custom) e `MethodArgumentNotValidException`
  (validacao de DTOs). Stack trace nunca e vazado. Suporta `X-Request-Id` (cabecalho ou ULID).

- **CurrentUser component**: Extrai `sub` como Long (id do usuario) e `roles` como List<String>.
  Nao confunde `anonymous` ou `svc:*` (seria erro em producao, aqui nao chega).

- **Mappers estaticos**: Nao criados em arquivo separado. DTOs (records) implementam `from()`
  como metodo estatico no proprio record.

- **Endpoint PATCH /users/me: validacao de corpo vazio**: Rejeita com 400 se todos os campos
  sao null. PUT nao tem essa validacao (PUT substituicao completa permite campos null).
