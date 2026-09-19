# Spec Delta

## Purpose

Gerencia o perfil do usuário, permitindo visualização do perfil próprio completo, edição de dados pessoais, e remoção de conta com notificação aos demais serviços por evento.

## ADDED Requirements

### Requirement: Obter perfil completo do usuário autenticado

O sistema SHALL retornar o perfil completo do usuário proprietário do token autenticado, incluindo todos os dados pessoais, papéis atribuídos e contagem de endereços cadastrados. Os dados devem ser retirados do banco de dados a cada requisição (sem cache).

#### Scenario: Perfil obtido com sucesso
- **WHEN** um usuário autenticado chama `GET /users/me` com token válido e escopo `users:read`
- **THEN** o sistema retorna status `200` com corpo contendo `id`, `name`, `email`, `cpf`, `phone`, `photoUrl`, `roles` (array com `customer` e/ou `owner`), `addressCount`, `createdAt`, `updatedAt`

#### Scenario: Token ausente ou inválido
- **WHEN** a requisição omite o header `Authorization` ou envia token expirado, malformado ou com assinatura inválida
- **THEN** o sistema retorna status `401`

#### Scenario: Escopo insuficiente
- **WHEN** o token é válido mas não inclui o escopo `users:read`
- **THEN** o sistema retorna status `403`

#### Scenario: Usuário removido
- **WHEN** o `sub` do token referencia um usuário com `deleted_at` preenchido
- **THEN** o sistema retorna status `404`

### Requirement: Editar dados pessoais do perfil próprio

O sistema SHALL permitir que o usuário altere apenas os campos editáveis do seu perfil: `name`, `cpf`, `phone` e `photoUrl`. Os campos `email` e `googleSub` MUST ser rejeitados como imutáveis, e a tentativa de alterá-los MUST resultar em erro `400`.

#### Scenario: Perfil atualizado com sucesso
- **WHEN** um usuário autenticado chama `PATCH /users/me` com corpo contendo ao menos um dos campos `name`, `cpf`, `phone` ou `photoUrl`, e todos os valores são válidos
- **THEN** o sistema retorna status `200` com o perfil atualizado completo; o cache `auth:profile:{userId}` é invalidado

#### Scenario: Tentativa de alterar campo imutável
- **WHEN** o corpo da requisição inclui `email` ou `googleSub`
- **THEN** o sistema retorna status `400`

#### Scenario: Corpo vazio
- **WHEN** o corpo da requisição não inclui nenhum campo alterável
- **THEN** o sistema retorna status `400`

#### Scenario: CPF duplicado
- **WHEN** o corpo inclui um CPF que já pertence a outro usuário ativo
- **THEN** o sistema retorna status `409` com código `DUPLICATE_CPF`

#### Scenario: CPF com dígitos verificadores inválidos
- **WHEN** o corpo inclui um CPF com número de dígitos correto (11) mas dígitos verificadores inválidos
- **THEN** o sistema retorna status `422` com código `INVALID_CPF`

#### Scenario: Telefone fora do formato E.164
- **WHEN** o corpo inclui um telefone que não corresponde ao padrão `^\+[1-9]\d{7,14}$`
- **THEN** o sistema retorna status `422` com código `INVALID_PHONE`

### Requirement: Remover conta do usuário

O sistema SHALL executar soft delete da conta do usuário, removendo todos os seus endereços na mesma transação e publicando o evento `ecommerce.user.deleted.v1` para os demais serviços anonimizarem dados pessoais. A conta do proprietário da loja MUST ser rejeitada com status `409`.

#### Scenario: Conta deletada com sucesso
- **WHEN** um usuário autenticado chama `DELETE /users/me` com escopo `users:write` e não é o proprietário da loja
- **THEN** o sistema retorna status `204`; `users.deleted_at` é preenchido; todos os endereços recebem soft delete (`address.deleted_at` preenchido); o evento `ecommerce.user.deleted.v1` é gravado na outbox na mesma transação

#### Scenario: Tentativa de remover conta da loja
- **WHEN** o usuário é o proprietário da loja (`owner` ativo)
- **THEN** o sistema retorna status `409` com código `STORE_OWNER_ACCOUNT`

#### Scenario: Conta já removida
- **WHEN** o `sub` do token referencia um usuário com `deleted_at` já preenchido
- **THEN** o sistema retorna status `404`

#### Scenario: Escopo insuficiente
- **WHEN** o token não inclui o escopo `users:write`
- **THEN** o sistema retorna status `403`

### Requirement: Obter perfil público reduzido de usuário

O sistema SHALL retornar um perfil público limitado de qualquer usuário, apenas com os dados necessários para atribuição de autoria (nome e foto).

#### Scenario: Perfil público obtido com sucesso
- **WHEN** qualquer cliente autenticado chama `GET /users/{id}` com `id` numérico válido e usuário ativo
- **THEN** o sistema retorna status `200` com corpo contendo apenas `id`, `name`, `photoUrl`, `memberSince`; resposta inclui o header `Cache-Control: public, max-age=300`

#### Scenario: Usuário inexistente
- **WHEN** o `id` é numérico mas não existe na tabela `users`
- **THEN** o sistema retorna status `404`

#### Scenario: Usuário removido
- **WHEN** o `id` referencia um usuário com `deleted_at` preenchido
- **THEN** o sistema retorna status `404`

#### Scenario: ID não numérico
- **WHEN** o `id` no path não é um número inteiro válido
- **THEN** o sistema retorna status `400`

### Requirement: Publicar evento de remoção de usuário

O sistema SHALL publicar um evento `ecommerce.user.deleted.v1` com key `userId` sempre que `DELETE /users/me` completar com sucesso. O evento deve ser gravado na tabela `outbox` na mesma transação da remoção.

#### Scenario: Evento publicado na outbox
- **WHEN** `DELETE /users/me` completa com status `204`
- **THEN** uma linha é gravada em `outbox` com `eventId` (UUID v4 único), `producedAt` (instante da transação), `aggregateType = 'user'`, `aggregateId = userId`, `topic = 'ecommerce.user.deleted.v1'`, `type = 'userDeleted'`, `event` contendo `{eventId, producedAt, userId, deletedAt}` (JSON)

#### Scenario: Consumo no inventory
- **WHEN** o evento `ecommerce.user.deleted.v1` chega no `inventory` via Kafka (tópico `ecommerce.user.deleted.v1`, alias `userDeleted`)
- **THEN** `review.user_name` é atualizado para `'Usuário removido'` e `review.user_photo_url` é zerado para NULL para todas as avaliações do usuário; resultado é `processado` ou `ignorado` se nada a anonimizar

#### Scenario: Consumo no order
- **WHEN** o evento `ecommerce.user.deleted.v1` chega no `order` via Kafka
- **THEN** soft delete (`deleted_at = now()`) é aplicado ao carrinho (`cart`) e todas as linhas (`cart_items`) do usuário; resultado é `processado` ou `ignorado` se sem carrinho
