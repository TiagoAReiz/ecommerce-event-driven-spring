# Spec Delta

## Purpose

Fornece rotas internas (servidor-a-servidor) para que os demais serviços hidratem dados de usuários em lote (para snapshots de avaliação) e consultem perfis com papéis (para autorização e auditoria), além de acesso a endereços inclusive removidos (para validar destinos de pedidos históricos).

## ADDED Requirements

### Requirement: Hidratar usuários em lote

O sistema SHALL retornar um snapshot de usuários para que o `inventory` popule o nome e foto nas avaliações. Ids inexistentes devem ser listados separadamente sem causar erro.

#### Scenario: Usuários hidratados com sucesso
- **WHEN** um serviço chama `GET /internal/users?ids=42,99,200` com escopo `internal:hydrate`
- **THEN** o sistema retorna status `200` com corpo contendo `users` (array com `id`, `name`, `photoUrl` de cada usuário encontrado, excluindo removidos) e `missing` (array dos ids que não existem)

#### Scenario: ID ausente ou vazio
- **WHEN** a query omite `ids` ou `ids` é uma string vazia
- **THEN** o sistema retorna status `400`

#### Scenario: Mais de 100 ids solicitados
- **WHEN** o parâmetro `ids` contém mais de 100 números
- **THEN** o sistema retorna status `400`

#### Scenario: IDs não numéricos
- **WHEN** o parâmetro `ids` inclui valores que não são números inteiros
- **THEN** o sistema retorna status `400`

#### Scenario: Alguns usuarios removidos
- **WHEN** o parâmetro `ids` inclui usuarios com `deleted_at` preenchido
- **THEN** o sistema não inclui esses usuários em `users`; seus ids vão para `missing`

#### Scenario: Escopo insuficiente
- **WHEN** o token não inclui o escopo `internal:hydrate`
- **THEN** o sistema retorna status `403`

### Requirement: Obter perfil do usuário com papéis

O sistema SHALL retornar o perfil completo de um usuário específico incluindo todos os seus papéis atribuídos (por exemplo, `customer` e `owner` para o proprietário da loja). Esta rota é usada para decidir autorização em tempo de requisição e para auditoria.

#### Scenario: Perfil obtido com papéis corretos
- **WHEN** um serviço chama `GET /internal/users/{id}/profile` com escopo `internal:hydrate` e `id` referencia um usuário ativo
- **THEN** o sistema retorna status `200` com corpo contendo `id`, `name`, `email`, `photoUrl`, `roles` (array com todos os papéis: sempre inclui `customer`, e inclui `owner` somente se for a conta da loja configurada)

#### Scenario: Usuário inexistente
- **WHEN** o `id` não existe na tabela `users`
- **THEN** o sistema retorna status `404`

#### Scenario: Usuário removido
- **WHEN** o `id` referencia um usuário com `deleted_at` preenchido
- **THEN** o sistema retorna status `404`

#### Scenario: ID não numérico
- **WHEN** o `id` no path não é um número inteiro válido
- **THEN** o sistema retorna status `400`

#### Scenario: Escopo insuficiente
- **WHEN** o token não inclui o escopo `internal:hydrate`
- **THEN** o sistema retorna status `403`

### Requirement: Consultar endereço completo inclusive removido

O sistema SHALL retornar um endereço completo que pertence a um usuário específico. Diferentemente da rota pública, **este endpoint enxerga endereços removidos** porque um pedido pode estar pendente com um endereço que o usuário já deletou, e o `shipment` precisa buscar o destino original para montar a etiqueta.

#### Scenario: Endereço obtido com sucesso
- **WHEN** um serviço chama `GET /internal/addresses/{id}?userId={userId}` com escopo `internal:hydrate`, `id` refere um endereço que pertence a `userId`, ativo ou removido
- **THEN** o sistema retorna status `200` com corpo contendo `id`, `idUser`, `name`, `zipcode`, `country`, `state`, `city`, `street`, `number` (endereço completo com todos os campos)

#### Scenario: Endereço de outro usuário
- **WHEN** o endereço `id` existe mas pertence a `userId` diferente do informado em query
- **THEN** o sistema retorna status `404`

#### Scenario: Endereço inexistente
- **WHEN** o `id` não existe na tabela `address`
- **THEN** o sistema retorna status `404`

#### Scenario: UserID ausente
- **WHEN** a query omite `userId`
- **THEN** o sistema retorna status `400`

#### Scenario: Parâmetros não numéricos
- **WHEN** `id` ou `userId` não são números inteiros válidos
- **THEN** o sistema retorna status `400`

#### Scenario: Escopo insuficiente
- **WHEN** o token não inclui o escopo `internal:hydrate`
- **THEN** o sistema retorna status `403`

#### Scenario: Usuário removido mas endereço ainda consultável
- **WHEN** o `userId` referencia um usuário com `deleted_at` preenchido, mas o `id` do endereço existe e pertence a ele
- **THEN** o sistema retorna o endereço normalmente com status `200` (soft delete do usuário não impede consulta de seus endereços passados)
