# Spec Delta

## Purpose

Fornece visibilidade do catálogo de produtos e categorias para clientes, com busca avançada, filtros, facetas e disponibilidade em tempo real.

## ADDED Requirements

### Requirement: Listar categorias

O sistema SHALL permitir a listagem de todas as categorias semeadas, com contagem de produtos ativos por categoria.

#### Scenario: Listar categorias com sucesso
- **WHEN** usuario com escopo `catalog:read` faz `GET /categories` sem parametros
- **THEN** sistema retorna lista de categorias com `id`, `name`, `slug` e `productCount`, cache de 1 hora, HTTP 200

#### Scenario: Incluir categorias vazias
- **WHEN** usuario com escopo `catalog:read` faz `GET /categories?includeEmpty=true`
- **THEN** sistema retorna categorias mesmo aquelas sem produtos, HTTP 200

#### Scenario: Parametro invalido
- **WHEN** usuario faz `GET /categories?includeEmpty=xyz`
- **THEN** sistema retorna HTTP 400 com ProblemDetail

### Requirement: Obter categoria por ID ou slug

O sistema SHALL permitir a busca de uma categoria especifica por ID numerico ou slug em kebab-case.

#### Scenario: Buscar categoria por ID
- **WHEN** usuario com escopo `catalog:read` faz `GET /categories/3`
- **THEN** sistema retorna categoria com `id`, `name`, `slug` e `productCount`, HTTP 200

#### Scenario: Buscar categoria por slug
- **WHEN** usuario com escopo `catalog:read` faz `GET /categories/eletronicos`
- **THEN** sistema retorna categoria com `id`, `name`, `slug` e `productCount`, HTTP 200

#### Scenario: Categoria nao encontrada
- **WHEN** usuario faz `GET /categories/9999` ou `GET /categories/invalido`
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Buscar produtos na vitrine

O sistema SHALL permitir busca textual em nome e descricao de produtos, com filtros por categoria, preco, avaliacao e disponibilidade, ordenacao por preco, avaliacao, data de criacao e nome, e facetas.

#### Scenario: Busca simples
- **WHEN** usuario com escopo `catalog:read` faz `GET /products?q=teclado`
- **THEN** sistema retorna produtos que contem "teclado" em nome ou descricao, paginados, HTTP 200

#### Scenario: Busca com minimo 2 caracteres
- **WHEN** usuario faz `GET /products?q=x`
- **THEN** sistema retorna HTTP 422 com ProblemDetail

#### Scenario: Filtro por categoria unica
- **WHEN** usuario faz `GET /products?categoryId=3`
- **THEN** sistema retorna apenas produtos da categoria 3, HTTP 200

#### Scenario: Filtro por categorias multiplas
- **WHEN** usuario faz `GET /products?categoryId=3&categoryId=5`
- **THEN** sistema retorna produtos de categoria 3 OU 5, HTTP 200

#### Scenario: Filtro por slug de categoria
- **WHEN** usuario faz `GET /products?categorySlug=eletronicos`
- **THEN** sistema retorna apenas produtos da categoria com slug "eletronicos", HTTP 200

#### Scenario: Filtro por faixa de preco
- **WHEN** usuario faz `GET /products?minPrice=100&maxPrice=500`
- **THEN** sistema retorna produtos com preco entre 100 e 500, HTTP 200

#### Scenario: Faixa de preco invalida
- **WHEN** usuario faz `GET /products?minPrice=500&maxPrice=100`
- **THEN** sistema retorna HTTP 400 com ProblemDetail

#### Scenario: Filtro por avaliacao minima
- **WHEN** usuario faz `GET /products?minRating=4.0`
- **THEN** sistema retorna apenas produtos com avaliacao >= 4.0, HTTP 200

#### Scenario: Filtro por disponibilidade
- **WHEN** usuario faz `GET /products?inStock=true`
- **THEN** sistema retorna apenas produtos com disponivel > 0, HTTP 200

#### Scenario: Ordenacao por preco
- **WHEN** usuario faz `GET /products?sort=price,asc`
- **THEN** sistema retorna produtos ordenados por preco, HTTP 200

#### Scenario: Ordenacao por avaliacao
- **WHEN** usuario faz `GET /products?sort=rating,desc`
- **THEN** sistema retorna produtos ordenados por avaliacao (maior primeiro), HTTP 200

#### Scenario: Campo de ordenacao invalido
- **WHEN** usuario faz `GET /products?sort=invalid,asc`
- **THEN** sistema retorna HTTP 400 com ProblemDetail

#### Scenario: Paginacao
- **WHEN** usuario faz `GET /products?page=1&size=50`
- **THEN** sistema retorna pagina 1 com 50 produtos, HTTP 200

#### Scenario: Tamanho de pagina muito grande
- **WHEN** usuario faz `GET /products?size=101`
- **THEN** sistema retorna HTTP 400 com ProblemDetail

#### Scenario: Facetas no resultado
- **WHEN** usuario faz `GET /products?q=teclado`
- **THEN** sistema retorna facetas com contagem de produtos por categoria e faixa de preco, HTTP 200

#### Scenario: Resultado vazio
- **WHEN** usuario faz `GET /products?q=xyz999`
- **THEN** sistema retorna pagina vazia (content: []), HTTP 200

### Requirement: Obter detalhe do produto

O sistema SHALL permitir a visualizacao completa de um produto incluindo nome, descricao, preco, categoria, fotos ordenadas e disponibilidade. Para o owner, o sistema SHALL incluir tambem o estoque bruto.

#### Scenario: Detalhe do produto para cliente
- **WHEN** usuario com escopo `catalog:read` faz `GET /products/118`
- **THEN** sistema retorna produto com id, name, description, price, available, rating, photos (ordenado por position), mas SEM o campo `stock`, HTTP 200

#### Scenario: Detalhe do produto para owner
- **WHEN** usuario com papeis `owner` e escopos `catalog:read` faz `GET /products/118`
- **THEN** sistema retorna produto incluindo o campo `stock` (estoque bruto), HTTP 200

#### Scenario: Produto nao encontrado
- **WHEN** usuario faz `GET /products/9999`
- **THEN** sistema retorna HTTP 404 com ProblemDetail

#### Scenario: Produto removido
- **WHEN** usuario faz `GET /products/{id}` para um produto com `deleted_at` preenchido
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Obter disponibilidade de produto

O sistema SHALL calcular a disponibilidade em tempo real como estoque bruto menos reservas ativas (held nao vencidas). Disponibilidade DEVE ser calculada a cada requisicao, NUNCA cacheada.

#### Scenario: Disponibilidade sem reservas
- **WHEN** usuario com escopo `catalog:read` faz `GET /products/118/availability` e produto tem stock=15 e nenhuma reserva held
- **THEN** sistema retorna { idProduct: 118, stock: 15, held: 0, available: 15, asOf: "..." }, HTTP 200

#### Scenario: Disponibilidade com reservas
- **WHEN** usuario faz `GET /products/118/availability` e produto tem stock=15 e 3 unidades em reservas held
- **THEN** sistema retorna { idProduct: 118, stock: 15, held: 3, available: 12, asOf: "..." }, HTTP 200

#### Scenario: Disponibilidade com reservas vencidas
- **WHEN** usuario faz `GET /products/118/availability` e produto tem stock=15, 3 unidades em held vencidas e 2 em held ativas
- **THEN** sistema retorna { idProduct: 118, stock: 15, held: 2, available: 13 }, as reservas vencidas nao entram na soma, HTTP 200

#### Scenario: Produto nao encontrado
- **WHEN** usuario faz `GET /products/9999/availability`
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Obter fotos do produto

O sistema SHALL permitir a listagem de fotos de um produto, ordenadas pela posicao.

#### Scenario: Listar fotos
- **WHEN** usuario com escopo `catalog:read` faz `GET /products/118/photos`
- **THEN** sistema retorna array de fotos ordenadas por position ascendente, cada uma com id, photoUrl e position, HTTP 200

#### Scenario: Produto sem fotos
- **WHEN** usuario faz `GET /products/118/photos` para um produto sem fotos
- **THEN** sistema retorna array vazio, HTTP 200

#### Scenario: Produto nao encontrado
- **WHEN** usuario faz `GET /products/9999/photos`
- **THEN** sistema retorna HTTP 404 com ProblemDetail

### Requirement: Cache de categoria e produto

O sistema SHALL manter em cache as categorias com validade de 1 hora e produtos individuais com validade de 10 minutos. Cache DEVE ser invalidado em qualquer escrita de produto ou foto.

#### Scenario: Cache de categoria
- **WHEN** usuario faz `GET /categories/3`
- **THEN** resposta inclui header `Cache-Control: public, max-age=3600`, HTTP 200

#### Scenario: Cache de produto
- **WHEN** usuario faz `GET /products/118`
- **THEN** segunda requisicao identica devem vir do cache, sem consulta ao banco, HTTP 200

#### Scenario: Cache invalidado na criacao de produto
- **WHEN** owner faz `POST /products` para criar um novo produto
- **THEN** cache de categorias eh invalidado, HTTP 201

#### Scenario: Cache invalidado na atualizacao de produto
- **WHEN** owner faz `PUT /products/118` para editar produto
- **THEN** cache de produto 118 e de categorias sao invalidados, HTTP 200

