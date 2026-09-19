# Spec Delta

## Purpose

Permite que o owner da loja gerencie o catálogo de produtos, ajuste estoques e administre fotos via URLs.

## ADDED Requirements

### Requirement: Listar produtos para gerenciamento

O sistema SHALL permitir ao owner visualizar todos os produtos (ativos, zerados e removidos) com filtros por status.

#### Scenario: Listar apenas produtos ativos
- **WHEN** owner com escopos `catalog:write` e papel `owner` faz `GET /products/manage?status=active`
- **THEN** sistema retorna produtos com `deleted_at` nulo e stock >= 0, paginados, HTTP 200

#### Scenario: Listar produtos zerados
- **WHEN** owner faz `GET /products/manage?status=out_of_stock`
- **THEN** sistema retorna produtos com stock == 0 e `deleted_at` nulo, HTTP 200

#### Scenario: Listar produtos removidos
- **WHEN** owner faz `GET /products/manage?status=deleted`
- **THEN** sistema retorna apenas produtos com `deleted_at` preenchido, HTTP 200

#### Scenario: Listar todos os produtos
- **WHEN** owner faz `GET /products/manage?status=all`
- **THEN** sistema retorna produtos em qualquer estado (ativo, zerado, removido), HTTP 200

#### Scenario: Sem permissao
- **WHEN** usuario sem papel `owner` faz `GET /products/manage`
- **THEN** sistema retorna HTTP 403 com ProblemDetail

#### Scenario: Sem escopo
- **WHEN** usuario sem escopo `catalog:write` faz `GET /products/manage`
- **THEN** sistema retorna HTTP 403 com ProblemDetail

### Requirement: Criar produto

O sistema SHALL permitir ao owner criar um novo produto com nome, descricao, preco e categoria obrigatoria.

#### Scenario: Criar produto com sucesso
- **WHEN** owner com escopos `catalog:write` e papel `owner` faz `POST /products` com body `{ "name": "Teclado", "description": "...", "price": "349.90", "categoryId": 3 }`
- **THEN** sistema cria produto com stock inicial 0, retorna 201 com location header e dados do produto criado, invalida cache de categorias

#### Scenario: Nome vazio
- **WHEN** owner faz `POST /products` com `name` vazio ou null
- **THEN** sistema retorna HTTP 422 com ProblemDetail e erro de validacao

#### Scenario: Preco negativo
- **WHEN** owner faz `POST /products` com `price` < 0
- **THEN** sistema retorna HTTP 422 com ProblemDetail

#### Scenario: Categoria nao existente
- **WHEN** owner faz `POST /products` com `categoryId` nao existente
- **WHEN** sistema retorna HTTP 422 com ProblemDetail

### Requirement: Atualizar produto

O sistema SHALL permitir ao owner editar nome, descricao, preco e categoria de um produto existente.

#### Scenario: Atualizar com sucesso
- **WHEN** owner com escopos `catalog:write` e papel `owner` faz `PUT /products/118` com `{ "name": "Novo Nome", "price": "399.90" }`
- **THEN** sistema atualiza produto, retorna HTTP 200, invalida cache do produto e de categorias

#### Scenario: Patch parcial
- **WHEN** owner faz `PATCH /products/118` com apenas `{ "price": "399.90" }`
- **THEN** sistema atualiza apenas o campo preco, retorna HTTP 200

#### Scenario: Produto nao encontrado
- **WHEN** owner faz `PUT /products/9999` com dados validos
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Excluir produto

O sistema SHALL permitir ao owner marcar um produto como removido (soft delete) se ele nao tiver reservas hold ativas.

#### Scenario: Excluir produto sem reservas
- **WHEN** owner com escopos `catalog:write` e papel `owner` faz `DELETE /products/118` e nao ha reservas held ativas
- **THEN** sistema marca `deleted_at` com timestamp atual, retorna HTTP 204, invalida caches

#### Scenario: Excluir produto com reservas hold ativas
- **WHEN** owner faz `DELETE /products/118` e existem reservas held com `expires_at > now()`
- **THEN** sistema retorna HTTP 409 com ProblemDetail e mensagem indicando conflito com reservas

#### Scenario: Produto ja removido
- **WHEN** owner faz `DELETE /products/{id}` para um produto ja marcado como deletado
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Gerenciar estoque do produto

O sistema SHALL permitir ao owner definir estoque absoluto ou ajustar por delta, com validacao de limite minimo reservado.

#### Scenario: Definir estoque absoluto
- **WHEN** owner com escopos `catalog:write` e papel `owner` faz `PATCH /products/118/stock` com `{ "stock": 50 }`
- **THEN** sistema define stock = 50, retorna HTTP 200 com disponibilidade atualizada, invalida cache

#### Scenario: Ajustar estoque por delta
- **WHEN** owner faz `PATCH /products/118/stock` com `{ "delta": 10 }`
- **THEN** sistema incrementa stock em 10, retorna HTTP 200

#### Scenario: Delta negativo
- **WHEN** owner faz `PATCH /products/118/stock` com `{ "delta": -5 }`
- **THEN** sistema decrementa stock em 5, retorna HTTP 200, desde que resultado final >= held reservado

#### Scenario: Resultado abaixo de reservado
- **WHEN** owner faz `PATCH /products/118/stock` com resultado menor que quantidade de reservas held ativas
- **THEN** sistema retorna HTTP 409 com ProblemDetail e mensagem indicando conflito com reservas

#### Scenario: Ambos stock e delta informados
- **WHEN** owner faz `PATCH /products/118/stock` com `{ "stock": 50, "delta": 10 }`
- **THEN** sistema retorna HTTP 400 com ProblemDetail, pois deve ser exatamente um

#### Scenario: Nenhum informado
- **WHEN** owner faz `PATCH /products/118/stock` com `{}`
- **THEN** sistema retorna HTTP 400 com ProblemDetail, pois precisa de um dos dois

### Requirement: Adicionar fotos ao produto

O sistema SHALL permitir ao owner adicionar fotos via URL absoluta HTTPS, com maximo de 10 fotos por produto e ordenacao por posicao.

#### Scenario: Adicionar foto com sucesso
- **WHEN** owner com escopos `catalog:write` e papel `owner` faz `POST /products/118/photos` com `{ "photoUrl": "https://cdn.loja.dev/p/118/0.webp" }`
- **THEN** sistema cria foto com position auto-incrementada, retorna HTTP 201, invalida cache do produto

#### Scenario: URL nao HTTPS
- **WHEN** owner faz `POST /products/118/photos` com `{ "photoUrl": "http://cdn.loja.dev/..." }`
- **THEN** sistema retorna HTTP 422 com ProblemDetail e mensagem sobre HTTPS obrigatoria

#### Scenario: URL invalida
- **WHEN** owner faz `POST /products/118/photos` com `{ "photoUrl": "nao-e-url" }`
- **THEN** sistema retorna HTTP 422 com ProblemDetail

#### Scenario: Maximo de fotos atingido
- **WHEN** owner faz `POST /products/118/photos` quando ja existem 10 fotos
- **THEN** sistema retorna HTTP 409 com ProblemDetail e mensagem sobre limite de fotos

#### Scenario: Produto nao encontrado
- **WHEN** owner faz `POST /products/9999/photos` com URL valida
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Reordenar fotos do produto

O sistema SHALL permitir ao owner alterar a ordem de exibicao das fotos de um produto.

#### Scenario: Reordenar fotos
- **WHEN** owner com escopos `catalog:write` e papel `owner` faz `PUT /products/118/photos/order` com `{ "photoIds": [901, 902, 903] }`
- **THEN** sistema atualiza position de cada foto conforme a ordem do array, retorna HTTP 200, invalida cache

#### Scenario: IDs de fotos inconsistentes
- **WHEN** owner faz `PUT /products/118/photos/order` com array que nao inclui todas as fotos do produto
- **THEN** sistema retorna HTTP 400 com ProblemDetail

#### Scenario: Foto de outro produto
- **WHEN** owner faz `PUT /products/118/photos/order` com array contendo photoId de outro produto
- **THEN** sistema retorna HTTP 400 com ProblemDetail

### Requirement: Remover foto do produto

O sistema SHALL permitir ao owner deletar uma foto, com reordenacao automatica das restantes.

#### Scenario: Remover foto com sucesso
- **WHEN** owner com escopos `catalog:write` e papel `owner` faz `DELETE /products/118/photos/901`
- **THEN** sistema remove foto, renumera positions das fotos restantes, retorna HTTP 204, invalida cache

#### Scenario: Foto nao encontrada
- **WHEN** owner faz `DELETE /products/118/photos/9999`
- **THEN** sistema retorna HTTP 404 com ProblemDetail

#### Scenario: Foto de outro produto
- **WHEN** owner faz `DELETE /products/118/photos/999` onde photoId 999 pertence a outro produto
- **THEN** sistema retorna HTTP 404 com ProblemDetail

