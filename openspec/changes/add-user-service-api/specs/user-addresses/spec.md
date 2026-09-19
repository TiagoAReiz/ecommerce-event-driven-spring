# Spec Delta

## Purpose

Gerencia os endereços de entrega do usuário conforme o modelo imutável: edições criam um novo endereço e removem o anterior, preservando histórico e garantindo que pedidos antigos apontam para o endereço correto da época.

## ADDED Requirements

### Requirement: Listar endereços do usuário

O sistema SHALL retornar uma página paginada de endereços ativos do usuário autenticado, ordenados por padrão por data de criação descendente.

#### Scenario: Endereços listados com sucesso
- **WHEN** um usuário autenticado chama `GET /users/me/addresses` com escopo `addresses:read` e parâmetros válidos de paginação
- **THEN** o sistema retorna status `200` com corpo contendo `content` (array de endereços), `page` (metadados: `number`, `size`, `totalElements`, `totalPages`)

#### Scenario: Parâmetros de paginação inválidos
- **WHEN** o parâmetro `size` é maior que 100, ou `sort` menciona um campo inexistente
- **THEN** o sistema retorna status `400`

#### Scenario: Escopo insuficiente
- **WHEN** o token não inclui o escopo `addresses:read`
- **THEN** o sistema retorna status `403`

#### Scenario: Lista vazia
- **WHEN** o usuário não tem endereços cadastrados ou a página solicitada está além da última
- **THEN** o sistema retorna status `200` com `content: []` (não `404`)

### Requirement: Obter endereço específico do usuário

O sistema SHALL retornar os detalhes de um endereço ativo que pertence ao usuário autenticado.

#### Scenario: Endereço obtido com sucesso
- **WHEN** um usuário autenticado chama `GET /users/me/addresses/{id}` com escopo `addresses:read` e `id` referencia um endereço seu, ativo
- **THEN** o sistema retorna status `200` com corpo contendo `id`, `name`, `zipcode`, `country`, `state`, `city`, `street`, `number`, `createdAt`

#### Scenario: Endereço inexistente
- **WHEN** o `id` não existe na tabela `address`
- **THEN** o sistema retorna status `404`

#### Scenario: Endereço removido
- **WHEN** o `id` referencia um endereço com `deleted_at` preenchido
- **THEN** o sistema retorna status `404`

#### Scenario: Endereço de outro usuário
- **WHEN** o `id` pertence a um outro usuário
- **THEN** o sistema retorna status `404` (não `403`)

#### Scenario: ID não numérico
- **WHEN** o `id` no path não é um número inteiro válido
- **THEN** o sistema retorna status `400`

### Requirement: Criar novo endereço

O sistema SHALL criar um novo endereço para o usuário autenticado com validação de formato de CEP e UF.

#### Scenario: Endereço criado com sucesso
- **WHEN** um usuário autenticado chama `POST /users/me/addresses` com escopo `addresses:write` e corpo contendo todos os campos obrigatórios válidos
- **THEN** o sistema retorna status `201` com corpo do novo endereço e header `Location: /users/me/addresses/{novoId}`

#### Scenario: Campo obrigatório ausente
- **WHEN** o corpo não inclui `zipcode`, `state`, `city` ou `street`
- **THEN** o sistema retorna status `400`

#### Scenario: Campo acima do tamanho máximo
- **WHEN** `name` tem mais de 60 caracteres
- **THEN** o sistema retorna status `400`

#### Scenario: CEP em formato inválido
- **WHEN** o `zipcode` não tem exatamente 8 dígitos
- **THEN** o sistema retorna status `400`

#### Scenario: UF em formato inválido
- **WHEN** `country = 'BR'` e `state` não tem exatamente 2 letras
- **THEN** o sistema retorna status `422` com código `INVALID_STATE`

#### Scenario: Limite de endereços excedido
- **WHEN** o usuário já tem 20 endereços criados no último 1 hora e tenta criar um novo
- **THEN** o sistema retorna status `429`

### Requirement: Substituir endereço completamente (PUT)

O sistema SHALL implementar a regra de imutabilidade: `PUT /users/me/addresses/{id}` cria um novo endereço com os dados fornecidos e faz soft delete do anterior, retornando o novo `id`. Campo omitido recebe `null`, diferentemente de `PATCH`.

#### Scenario: Endereço substituído com sucesso
- **WHEN** um usuário autenticado chama `PUT /users/me/addresses/{id}` com escopo `addresses:write`, `id` referencia um endereço seu ativo, e o corpo é válido
- **THEN** o sistema retorna status `200` com corpo do novo endereço (incluindo novo `id` gerado); o endereço antigo recebe `deleted_at = now()` na mesma transação

#### Scenario: Campo obrigatório ausente
- **WHEN** o corpo não inclui `state`, `city` ou `street`
- **THEN** o sistema retorna status `400`

#### Scenario: Endereço de outro usuário
- **WHEN** o `id` pertence a outro usuário
- **THEN** o sistema retorna status `404`

#### Scenario: Validações de CEP/UF falham
- **WHEN** o `zipcode` ou `state` são inválidos conforme as mesmas regras de `POST`
- **THEN** o sistema retorna status `422`

### Requirement: Editar parcialmente endereço (PATCH)

O sistema SHALL implementar a regra de imutabilidade: `PATCH /users/me/addresses/{id}` funciona como `PUT` (cria novo, remove antigo), mas aceita apenas os campos fornecidos no corpo, preenchendo os demais com os valores do endereço anterior.

#### Scenario: Endereço editado com sucesso
- **WHEN** um usuário autenticado chama `PATCH /users/me/addresses/{id}` com escopo `addresses:write`, `id` referencia um endereço seu ativo, e o corpo contém ao menos um campo alterável válido
- **THEN** o sistema retorna status `200` com corpo do novo endereço (com `id` gerado); campos não fornecidos herdam valores do endereço antigo; endereço antigo recebe soft delete

#### Scenario: Corpo vazio
- **WHEN** o corpo da requisição não inclui nenhum campo
- **THEN** o sistema retorna status `400`

#### Scenario: Apenas parte dos campos inválida
- **WHEN** o corpo inclui alguns campos válidos e outros inválidos
- **THEN** o sistema retorna status `400` ou `422` conforme a natureza do erro

### Requirement: Remover endereço

O sistema SHALL fazer soft delete de um endereço ativo que pertença ao usuário. Endereço já usado em envio concluído pode ser removido sem impedir que o histórico de envios permaneça íntegro, porque o `shipment` guarda snapshot dos dados em `to_*` e `from_*`.

#### Scenario: Endereço removido com sucesso
- **WHEN** um usuário autenticado chama `DELETE /users/me/addresses/{id}` com escopo `addresses:write`, `id` referencia um endereço seu ativo
- **THEN** o sistema retorna status `204`; `address.deleted_at` é preenchido

#### Scenario: Endereço de outro usuário
- **WHEN** o `id` pertence a outro usuário
- **THEN** o sistema retorna status `404`

#### Scenario: Endereço já removido
- **WHEN** o `id` referencia um endereço com `deleted_at` já preenchido
- **THEN** o sistema retorna status `404`

#### Scenario: Endereço é destino de envio não finalizado
- **WHEN** o endereço tem referência em `shipment.id_address` com `status NOT IN ('delivered', 'returned', 'cancelled')`
- **THEN** o sistema retorna status `409` com código `ADDRESS_IN_ACTIVE_SHIPMENT`
